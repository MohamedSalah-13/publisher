package com.hamzatech.publisher.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.hamzatech.publisher.http.HttpClients;
import com.hamzatech.publisher.model.EffectiveSettings;
import com.hamzatech.publisher.service.ConfigService;
import lombok.extern.log4j.Log4j2;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * يشارك رابط المقال على صفحة فيسبوك عبر Graph API ({@code POST /{page-id}/feed}).
 * <p>
 * Meta لا تسمح بالنشر على الحسابات الشخصية (منذ 2018) ولا في المجموعات (أُزيلت
 * الواجهة في 2024)؛ الصفحات وحدها ممكنة، بصلاحيتَي {@code pages_manage_posts}
 * و{@code pages_read_engagement}.
 * <p>
 * التوكن في الإعدادات ({@link ConfigService#FACEBOOK_PAGE_TOKEN}) يقبل نوعين:
 * <ul>
 *     <li><b>Page Access Token</b> طويل الأمد لصفحة واحدة — لا تنتهي صلاحيته.</li>
 *     <li><b>User Access Token</b> طويل الأمد لمن يدير عدة صفحات: يُطلب منه توكن كل
 *     صفحة ({@code GET /{page-id}?fields=access_token}) ويُحفظ في الذاكرة.</li>
 * </ul>
 * إن لم يُرجِع ذلك الطلب توكنًا (لأن التوكن المضبوط توكن صفحة أصلًا) يُستخدم كما هو.
 */
@Log4j2
public class FacebookPageSharer implements SocialSharer {

    public static final String ID = "facebook";

    /**
     * إصدار Graph API. كل إصدار يبقى متاحًا نحو سنتين؛ الطلب بلا إصدار يذهب إلى
     * أقدم إصدار يدعمه التطبيق، وهو أسوأ من تثبيت إصدار معروف.
     */
    static final String GRAPH_VERSION = "v24.0";

    private static final String DEFAULT_GRAPH_URL = "https://graph.facebook.com";

    private final ConfigService configService = ConfigService.getInstance();
    private final String graphUrl;
    private final OkHttpClient client = HttpClients.standard();
    /** توكن كل صفحة، مفتاحه الصفحة + التوكن المضبوط، فتغيير التوكن يُبطله. */
    private final Map<String, String> pageTokens = new ConcurrentHashMap<>();

    public FacebookPageSharer() {
        this(DEFAULT_GRAPH_URL);
    }

    /** @param graphUrl عنوان Graph API، يُستبدَل في الاختبارات بخادم محلي */
    FacebookPageSharer(String graphUrl) {
        this.graphUrl = graphUrl.replaceAll("/+$", "");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Facebook";
    }

    @Override
    public boolean isConfigured() {
        String token = configService.getFacebookPageToken();
        return token != null && !token.isBlank();
    }

    @Override
    public String destination(EffectiveSettings settings) {
        return normalizePageId(settings.facebookPageId());
    }

    @Override
    public String share(SharedPost post, String destination) throws IOException {
        String token = configService.getFacebookPageToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Facebook page token is missing. Add it in the settings.");
        }
        token = token.trim();
        String pageId = normalizePageId(destination);
        String cacheKey = pageId + "|" + token.hashCode();
        String pageToken = pageTokens.get(cacheKey);
        if (pageToken == null) {
            pageToken = lookupPageToken(pageId, token);
            pageTokens.put(cacheKey, pageToken);
        }

        Request request = new Request.Builder()
                .url(graphUrl + "/" + GRAPH_VERSION + "/" + pageId + "/feed")
                .post(new FormBody.Builder()
                        .add("message", message(post))
                        .add("link", post.url())
                        .add("access_token", pageToken)
                        .build())
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonObject json = parse(response.body() != null ? response.body().string() : "");
            if (!response.isSuccessful() || json == null || !json.has("id")) {
                if (response.code() == 400 || response.code() == 401 || response.code() == 403) {
                    // توكن انتهى أو سُحبت صلاحيته: لا تُبقِ توكن الصفحة المحفوظ
                    pageTokens.remove(cacheKey);
                }
                throw new IOException("Facebook: " + errorMessage(json, response.code()));
            }
            return postLink(json.get("id").getAsString());
        }
    }

    /**
     * توكن الصفحة من توكن مستخدم يديرها، أو التوكن المضبوط نفسه إن لم يكن ذلك ممكنًا.
     * <p>
     * لا يُفشل المشاركة أبدًا: إن رُفض التوكن حقًّا فسيُرفض طلب النشر بعده برسالة
     * فيسبوك الواضحة، وهي أنفع للمستخدم من خطأ هذا الطلب التمهيدي.
     */
    private String lookupPageToken(String pageId, String token) {
        HttpUrl url = HttpUrl.get(graphUrl + "/" + GRAPH_VERSION + "/" + pageId).newBuilder()
                .addQueryParameter("fields", "access_token")
                .addQueryParameter("access_token", token)
                .build();
        try (Response response = client.newCall(new Request.Builder().url(url).get().build()).execute()) {
            JsonObject json = parse(response.body() != null ? response.body().string() : "");
            if (response.isSuccessful() && json != null && json.has("access_token")) {
                return json.get("access_token").getAsString();
            }
        } catch (IOException e) {
            log.debug("Facebook page token lookup failed: {}", e.getClass().getSimpleName());
        }
        return token;
    }

    /** نص المنشور: العنوان ثم الوصف. الرابط يُرسَل منفصلًا فيعرض فيسبوك معاينته. */
    static String message(SharedPost post) {
        String title = post.title() == null ? "" : post.title().strip();
        String description = post.description() == null ? "" : post.description().strip();
        if (description.isEmpty()) {
            return title;
        }
        return title.isEmpty() ? description : title + "\n\n" + description;
    }

    /** يقبل المعرّف الرقمي أو رابط الصفحة {@code facebook.com/<name-or-id>}. */
    static String normalizePageId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String id = raw.strip()
                .replaceFirst("^(?i)(https?://)?(www\\.|m\\.)?(facebook|fb)\\.com/", "")
                .replaceAll("[/?#].*$", "");
        return id.isEmpty() ? null : id;
    }

    /** المعرّف المُرجَع بصيغة {@code <page-id>_<post-id>}، ورابطه يفتح المنشور. */
    static String postLink(String id) {
        return "https://www.facebook.com/" + id;
    }

    private static String errorMessage(JsonObject json, int code) {
        if (json != null && json.has("error") && json.get("error").isJsonObject()) {
            JsonObject error = json.getAsJsonObject("error");
            if (error.has("message")) {
                return error.get("message").getAsString();
            }
        }
        return "HTTP " + code;
    }

    private static JsonObject parse(String text) {
        try {
            JsonElement element = JsonParser.parseString(text);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }
}
