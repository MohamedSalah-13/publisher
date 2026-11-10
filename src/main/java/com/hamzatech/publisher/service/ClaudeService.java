package com.hamzatech.publisher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hamzatech.publisher.http.HttpClients;
import com.hamzatech.publisher.util.HtmlCleaner;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;

/**
 * توليد المقالات عبر Claude (Anthropic Messages API).
 */
public class ClaudeService {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * حد الرموز للردود القصيرة (عناوين، ترجمة، SEO). النماذج الحديثة تفكّر قبل
     * الإجابة، والتفكير يُحسب من هذا الحد؛ حد صغير مثل 200 أو 1500 كان ينفد
     * قبل أن يكتب النموذج الرد نفسه. يُدفع ثمن ما يُستهلك فقط، لا الحد.
     */
    public static final int SHORT_REPLY_MAX_TOKENS = 16000;

    private static final String ARTICLE_TRUNCATED =
            "توقف Claude قبل إكمال المقال (بلغ الحد الأقصى للطول). اختر طولًا أقصر في الإعدادات.";

    private final String apiKey;
    private final String apiUrl;
    private final OkHttpClient client;

    public ClaudeService(String apiKey) {
        this(apiKey, API_URL);
    }

    /**
     * @param apiUrl عنوان الواجهة؛ يُمرَّر في الاختبارات لتوجيه الطلب إلى خادم محلي
     */
    ClaudeService(String apiKey, String apiUrl) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        // مقال طويل بالعربية قد يتجاوز دقيقتين من التوليد دون بث، وهي مهلة العميل
        // "الطويل" الافتراضية
        this.client = HttpClients.withReadTimeout(Duration.ofMinutes(5));
    }

    public String generateArticle(String title, String targetLanguage, String model) throws IOException {
        ArticlePrompt prompt = ArticlePrompt.forArticle(title, targetLanguage);

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt.user());
        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.addProperty("max_tokens", ArticlePrompt.maxOutputTokens(ConfigService.getInstance().getArticleLength()));
        payload.addProperty("system", prompt.system());
        payload.add("messages", messages);

        return HtmlCleaner.clean(send(payload, model));
    }

    /**
     * ترجمة قصيرة (عنوان مثلًا) — تُستخدم من {@link TranslationService}.
     */
    public String complete(String prompt, String model, int maxTokens) throws IOException {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.addProperty("max_tokens", maxTokens);
        payload.add("messages", messages);

        return send(payload, model, "توقف Claude قبل إكمال الرد (بلغ الحد الأقصى للرموز).").trim();
    }

    private String send(JsonObject payload, String model) throws IOException {
        return send(payload, model, ARTICLE_TRUNCATED);
    }

    private String send(JsonObject payload, String model, String truncatedMessage) throws IOException {
        Request request = new Request.Builder()
                .url(apiUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .post(RequestBody.create(payload.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("خطأ من Claude (" + model + "): " + response.code() + " - " + errorMessage(responseBody));
            }
            return extractText(responseBody, truncatedMessage);
        }
    }

    /**
     * يجمع كتل النص في الرد.
     * <p>
     * توقّف النموذج عند حد الرموز يعني مقالًا مقطوعًا في منتصفه؛ نشره أسوأ من
     * الفشل، فيُعامَل كخطأ.
     */
    static String extractText(String responseBody) throws IOException {
        return extractText(responseBody, ARTICLE_TRUNCATED);
    }

    private static String extractText(String responseBody, String truncatedMessage) throws IOException {
        JsonObject json;
        try {
            json = JsonParser.parseString(responseBody).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("رد Claude غير مفهوم: " + responseBody, e);
        }

        if (json.has("stop_reason") && "max_tokens".equals(json.get("stop_reason").getAsString())) {
            throw new IOException(truncatedMessage);
        }

        JsonArray content = json.has("content") && json.get("content").isJsonArray()
                ? json.getAsJsonArray("content") : new JsonArray();
        StringBuilder text = new StringBuilder();
        for (JsonElement block : content) {
            JsonObject obj = block.getAsJsonObject();
            if (obj.has("type") && "text".equals(obj.get("type").getAsString()) && obj.has("text")) {
                text.append(obj.get("text").getAsString());
            }
        }

        if (text.isEmpty()) {
            throw new IOException("رد Claude لا يحتوي على نص: " + responseBody);
        }
        return text.toString();
    }

    private static String errorMessage(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error") && json.getAsJsonObject("error").has("message")) {
                return json.getAsJsonObject("error").get("message").getAsString();
            }
        } catch (RuntimeException ignored) {
            // ليس JSON؛ نعيد النص كما هو
        }
        return responseBody.isBlank() ? "<empty>" : responseBody;
    }
}
