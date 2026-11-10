package com.hamzatech.publisher.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.hamzatech.publisher.http.HttpClients;
import com.hamzatech.publisher.model.EffectiveSettings;
import com.hamzatech.publisher.service.ConfigService;
import lombok.extern.log4j.Log4j2;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

/**
 * يشارك المقال في قناة تيليجرام عبر Bot API.
 * <p>
 * البوت يُنشأ من {@code @BotFather} ويُضاف مشرفًا في القناة بصلاحية النشر؛ توكنه
 * واحد لكل القنوات ({@link ConfigService#TELEGRAM_BOT_TOKEN})، والقناة نفسها تُضبط
 * لكل وجهة في ملف تعريفها.
 * <p>
 * التوكن جزء من مسار الرابط ({@code /bot<token>/sendMessage})، ورسالة
 * {@code RetryInterceptor} عند استنفاد المحاولات تحمل الرابط كاملًا — لذلك تُنقّى
 * كل رسالة خطأ من التوكن قبل أن تخرج من هذا الكلاس إلى سجل النشر.
 */
@Log4j2
public class TelegramSharer implements SocialSharer {

    public static final String ID = "telegram";

    /** حد الشرح تحت الصورة في تيليجرام، بعد حذف وسوم HTML. */
    static final int CAPTION_LIMIT = 1024;
    /** حد الرسالة النصية في تيليجرام. */
    static final int MESSAGE_LIMIT = 4096;

    private static final String DEFAULT_API_URL = "https://api.telegram.org";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ConfigService configService = ConfigService.getInstance();
    private final String apiUrl;
    private final OkHttpClient client = HttpClients.standard();

    public TelegramSharer() {
        this(DEFAULT_API_URL);
    }

    /** @param apiUrl عنوان Bot API، يُستبدَل في الاختبارات بخادم محلي */
    TelegramSharer(String apiUrl) {
        this.apiUrl = apiUrl.replaceAll("/+$", "");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Telegram";
    }

    @Override
    public boolean isConfigured() {
        String token = configService.getTelegramBotToken();
        return token != null && !token.isBlank();
    }

    @Override
    public String destination(EffectiveSettings settings) {
        return normalizeChat(settings.telegramChat());
    }

    @Override
    public String share(SharedPost post, String destination) throws IOException {
        String token = configService.getTelegramBotToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Telegram bot token is missing. Add it in the settings.");
        }
        token = token.trim();
        String chat = normalizeChat(destination);

        try {
            JsonObject result;
            if (post.imageUrl() != null) {
                try {
                    result = call(token, "sendPhoto", photoRequest(chat, post));
                } catch (TelegramException e) {
                    if (e.code != 400) {
                        throw e;
                    }
                    // 400 هنا غالبًا صورة لم يستطع تيليجرام جلبها؛ الرسالة النصية تعرض
                    // معاينة الرابط بصورة المقال نفسها، فلا داعي لإفشال المشاركة
                    log.warn("Telegram rejected the photo ({}); sending a text message instead.", e.getMessage());
                    result = call(token, "sendMessage", messageRequest(chat, post));
                }
            } else {
                result = call(token, "sendMessage", messageRequest(chat, post));
            }
            return messageLink(result);
        } catch (IOException e) {
            throw new IOException(redact(e.getMessage(), token), e);
        }
    }

    private JsonObject call(String token, String method, JsonObject body) throws IOException {
        Request request = new Request.Builder()
                .url(apiUrl + "/bot" + token + "/" + method)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            JsonObject json = parse(text);
            boolean ok = json != null && json.has("ok") && json.get("ok").getAsBoolean();
            if (!response.isSuccessful() || !ok) {
                String description = json != null && json.has("description")
                        ? json.get("description").getAsString()
                        : "HTTP " + response.code();
                throw new TelegramException(response.code(), "Telegram: " + description);
            }
            return json.has("result") && json.get("result").isJsonObject()
                    ? json.getAsJsonObject("result") : new JsonObject();
        }
    }

    static JsonObject photoRequest(String chat, SharedPost post) {
        JsonObject body = new JsonObject();
        body.addProperty("chat_id", chat);
        body.addProperty("photo", post.imageUrl());
        body.addProperty("caption", text(post, CAPTION_LIMIT));
        body.addProperty("parse_mode", "HTML");
        return body;
    }

    static JsonObject messageRequest(String chat, SharedPost post) {
        JsonObject preview = new JsonObject();
        preview.addProperty("url", post.url());
        preview.addProperty("prefer_large_media", true);

        JsonObject body = new JsonObject();
        body.addProperty("chat_id", chat);
        body.addProperty("text", text(post, MESSAGE_LIMIT));
        body.addProperty("parse_mode", "HTML");
        body.add("link_preview_options", preview);
        return body;
    }

    /**
     * نص المنشور: العنوان بخط عريض، ثم الوصف، ثم الرابط.
     * <p>
     * الحد يُحسب على النص الظاهر — تيليجرام يعدّه بعد حذف الوسوم — فيُقصّ الوصف
     * أولًا لأن الرابط لا يُقصّ والعنوان أهم من الوصف.
     */
    static String text(SharedPost post, int limit) {
        String url = post.url().strip();
        String title = post.title() == null ? "" : post.title().strip();
        String description = post.description() == null ? "" : post.description().strip();

        title = truncate(title, Math.max(0, limit - url.length() - 2));
        int room = limit - title.length() - url.length() - 4;
        description = room >= 20 ? truncate(description, room) : "";

        StringBuilder text = new StringBuilder();
        if (!title.isEmpty()) {
            text.append("<b>").append(escape(title)).append("</b>\n\n");
        }
        if (!description.isEmpty()) {
            text.append(escape(description)).append("\n\n");
        }
        return text.append(escape(url)).toString();
    }

    /**
     * يقبل ما يكتبه المستخدم عادة: {@code @channel} أو {@code channel} أو رابط
     * {@code t.me/channel} أو المعرّف الرقمي {@code -100...} للقنوات الخاصة.
     *
     * @return المعرّف بصيغة Bot API، أو {@code null} للقيمة الفارغة
     */
    static String normalizeChat(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String chat = raw.strip()
                .replaceFirst("^(?i)(https?://)?(www\\.)?(t|telegram)\\.me/", "")
                .replaceAll("/+$", "");
        if (chat.matches("-?\\d+") || chat.startsWith("@")) {
            return chat;
        }
        return "@" + chat;
    }

    /**
     * رابط الرسالة في القناة: {@code t.me/<username>/<id>} للقناة العامة،
     * و{@code t.me/c/<id>/<id>} للخاصة (يفتحه أعضاؤها فقط).
     */
    static String messageLink(JsonObject result) {
        if (!result.has("message_id") || !result.has("chat")) {
            return null;
        }
        long messageId = result.get("message_id").getAsLong();
        JsonObject chat = result.getAsJsonObject("chat");
        if (chat.has("username")) {
            return "https://t.me/" + chat.get("username").getAsString() + "/" + messageId;
        }
        if (chat.has("id")) {
            String id = chat.get("id").getAsString();
            if (id.startsWith("-100")) {
                return "https://t.me/c/" + id.substring(4) + "/" + messageId;
            }
        }
        return null;
    }

    static String redact(String message, String token) {
        if (message == null) {
            return "Telegram request failed";
        }
        return message.replace(token, "<bot-token>");
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        if (max <= 1) {
            return "";
        }
        int end = max - 1;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end).stripTrailing() + "…";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static JsonObject parse(String text) {
        try {
            var element = JsonParser.parseString(text);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    /** خطأ من تيليجرام مع رمز HTTP، ليُميَّز رفض الصورة عن غيره. */
    private static final class TelegramException extends IOException {
        private final int code;

        TelegramException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
