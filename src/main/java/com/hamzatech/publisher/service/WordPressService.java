package com.hamzatech.publisher.service;

import com.hamzatech.publisher.http.HttpClients;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * خدمة نشر المقالات على مواقع WordPress عبر REST API باستخدام Application Password
 * (متوفرة افتراضياً من ووردبريس 5.6+، أو بواسطة إضافة Application Passwords فيما قبلها).
 */
@Log4j2
public class WordPressService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;

    public WordPressService() {
        this.client = HttpClients.standard();
    }

    /**
     * توحيد شكل عنوان الموقع: إزالة المسافات والشرطات الأخيرة، وافتراض {@code https://}
     * عند كتابة النطاق بلا بروتوكول — فالاتصال المشفّر هو الافتراض المطلوب، كما أن
     * العنوان بلا بروتوكول لا يمكن تحليله كرابط أصلًا.
     */
    public static String normalizeUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.isEmpty() && !trimmed.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            trimmed = "https://" + trimmed;
        }
        return trimmed;
    }

    /**
     * هل يجوز إرسال بيانات الاعتماد إلى هذا العنوان؟
     * <p>
     * Basic Auth يرسل كلمة مرور التطبيق مُرمَّزة بـ Base64 فقط، وهي قابلة للفك فورًا،
     * فأي طلب عبر {@code http://} يكشف الكلمة لأي متنصت على الشبكة. لذلك نشترط HTTPS،
     * ونستثني عناوين الجهاز المحلي وحدها لأن حركتها لا تغادر الجهاز أصلًا.
     * <p>
     * هذه هي المرجع الوحيد للقاعدة: تستدعيها الواجهة لعرض رسالة مترجمة قبل الحفظ،
     * وتستدعيها هذه الخدمة نفسها قبل كل طلب.
     */
    public static boolean isSecureUrl(String siteUrl) {
        if (siteUrl == null || siteUrl.isBlank()) {
            return false;
        }
        HttpUrl url = HttpUrl.parse(siteUrl.trim());
        return url != null && (url.isHttps() || isLoopback(url.host()));
    }

    /**
     * هل يشير هذا المضيف إلى الجهاز نفسه؟
     * <p>
     * لا تكفي مقارنة الأسماء الحرفية: التطوير المحلي كثيرًا ما يجري خلف اسم مستعار
     * في ملف hosts أو نطاق فرعي تحت {@code .localhost}، وكلاهما يشير إلى
     * 127.0.0.1. لذلك نستفسر عن العنوان الفعلي، ولا نعدّ المضيف محليًا إلا إذا كانت
     * كل عناوينه محلية — اسم يشير إلى عنوانين أحدهما عام ليس محليًا.
     */
    private static boolean isLoopback(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost") || normalized.equals("127.0.0.1")
                || normalized.equals("[::1]") || normalized.equals("::1")
                || normalized.endsWith(".localhost")) {
            return true;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (!address.isLoopbackAddress()) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            // مضيف لا يُحلّ ليس محليًا، والطلب سيفشل لاحقًا برسالته الخاصة
            return false;
        }
    }

    /**
     * يتحقق من العنوان ويبني منه رابط نقطة النهاية المطلوبة.
     */
    private static HttpUrl endpoint(String siteUrl, String path) throws IOException {
        HttpUrl base = siteUrl == null ? null : HttpUrl.parse(siteUrl.trim());
        if (base == null) {
            throw new IOException("عنوان موقع ووردبريس غير صالح: " + siteUrl);
        }
        if (!isSecureUrl(siteUrl)) {
            throw new IOException("لن تُرسل كلمة مرور التطبيق عبر اتصال غير مشفّر. "
                    + "استخدم https:// لهذا الموقع بدل http:// — العنوان الحالي: " + siteUrl);
        }
        return base.newBuilder().addPathSegments(path).build();
    }

    /**
     * نشر مقال جديد.
     *
     * @return رابط المقال المنشور
     */
    public String publishPost(String siteUrl, String username, String applicationPassword,
                               String title, String contentHtml, String slug) throws IOException {
        return publishPost(siteUrl, username, applicationPassword, title, contentHtml, slug, null);
    }

    public String publishPost(String siteUrl, String username, String applicationPassword,
                               String title, String contentHtml, String slug, Integer categoryId) throws IOException {
        return publishPost(siteUrl, username, applicationPassword, title, contentHtml, slug, categoryId, null);
    }

    /**
     * @param categoryId      معرّف التصنيف، أو {@code null} للنشر بلا تصنيف
     * @param featuredMediaId معرّف الصورة البارزة، أو {@code null} لتركها فارغة
     */
    public String publishPost(String siteUrl, String username, String applicationPassword,
                               String title, String contentHtml, String slug,
                               Integer categoryId, Integer featuredMediaId) throws IOException {
        return publishPost(siteUrl, username, applicationPassword, title, contentHtml, slug,
                categoryId, featuredMediaId, false);
    }

    /**
     * @param draft {@code true} لحفظ المقال مسودة بدل نشره؛ يُعاد حينها رابط
     *              تحريره في لوحة التحكم، فرابطه العام لا يعمل قبل النشر
     */
    public String publishPost(String siteUrl, String username, String applicationPassword,
                               String title, String contentHtml, String slug,
                               Integer categoryId, Integer featuredMediaId, boolean draft) throws IOException {
        return createPost(siteUrl, username, applicationPassword, title, contentHtml, slug,
                new PostOptions(categoryId, featuredMediaId, draft, null, List.of()));
    }

    /**
     * الحقول الاختيارية للمقال. كل حقل فارغ يُترك خارج الطلب فيأخذ افتراضي ووردبريس.
     *
     * @param excerpt وصف قصير؛ القوالب وإضافات SEO تعرضه وصفًا للمقال في نتائج البحث
     * @param tagIds  معرّفات الوسوم، لا أسماؤها — انظر {@link #findOrCreateTagId}
     */
    public record PostOptions(Integer categoryId, Integer featuredMediaId, boolean draft,
                              String excerpt, List<Integer> tagIds) {
        public PostOptions {
            tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
        }
    }

    public String createPost(String siteUrl, String username, String applicationPassword,
                              String title, String contentHtml, String slug, PostOptions options)
            throws IOException {
        boolean draft = options.draft();
        JsonObject body = new JsonObject();
        body.addProperty("title", title);
        body.addProperty("content", contentHtml);
        body.addProperty("status", draft ? "draft" : "publish");
        if (slug != null && !slug.isBlank()) {
            body.addProperty("slug", slug);
        }
        if (options.categoryId() != null) {
            JsonArray categories = new JsonArray();
            categories.add(options.categoryId());
            body.add("categories", categories);
        }
        if (options.featuredMediaId() != null) {
            body.addProperty("featured_media", options.featuredMediaId());
        }
        if (options.excerpt() != null && !options.excerpt().isBlank()) {
            body.addProperty("excerpt", options.excerpt());
        }
        if (!options.tagIds().isEmpty()) {
            JsonArray tags = new JsonArray();
            options.tagIds().forEach(tags::add);
            body.add("tags", tags);
        }

        Request request = new Request.Builder()
                .url(endpoint(siteUrl, "wp-json/wp/v2/posts"))
                .header("Authorization", basicAuthHeader(username, applicationPassword))
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("خطأ من WordPress (" + response.code() + "): " + extractError(responseBody));
            }

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (draft && json.has("id")) {
                return endpoint(siteUrl, "wp-admin/post.php").newBuilder()
                        .addQueryParameter("post", json.get("id").getAsString())
                        .addQueryParameter("action", "edit")
                        .build().toString();
            }
            if (json.has("link")) {
                return json.get("link").getAsString();
            }
            return siteUrl;
        }
    }

    /**
     * التحقق من صحة بيانات الاتصال بموقع ووردبريس (يُستخدم في شاشة إدارة المواقع).
     */
    public void testConnection(String siteUrl, String username, String applicationPassword) throws IOException {
        Request request = new Request.Builder()
                .url(endpoint(siteUrl, "wp-json/wp/v2/users/me"))
                .header("Authorization", basicAuthHeader(username, applicationPassword))
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new IOException("فشل الاتصال (" + response.code() + "): " + extractError(responseBody));
            }
        }
    }

    /**
     * تصنيفات الموقع بأسمائها وعدد مقالات كلٍّ منها، لعرضها في اللوحة كما تُعرض
     * وسوم Blogger.
     * <p>
     * {@code hide_empty=false} مقصود: تصنيف فارغ هو غالبًا ما أنشأه المستخدم
     * ليملأه، فإخفاؤه يجعله غير قابل للاختيار.
     */
    public Map<String, Integer> listCategories(String siteUrl, String username, String applicationPassword)
            throws IOException {
        HttpUrl url = endpoint(siteUrl, "wp-json/wp/v2/categories").newBuilder()
                .addQueryParameter("per_page", "100")
                .addQueryParameter("hide_empty", "false")
                .addQueryParameter("orderby", "count")
                .addQueryParameter("order", "desc")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", basicAuthHeader(username, applicationPassword))
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("تعذر جلب تصنيفات ووردبريس (" + response.code() + "): " + extractError(body));
            }

            Map<String, Integer> categories = new LinkedHashMap<>();
            for (JsonElement element : JsonParser.parseString(body).getAsJsonArray()) {
                JsonObject category = element.getAsJsonObject();
                if (category.has("name")) {
                    categories.put(category.get("name").getAsString(),
                            category.has("count") ? category.get("count").getAsInt() : 0);
                }
            }
            return categories;
        }
    }

    /**
     * معرّف التصنيف بالاسم، ويُنشأ إن لم يوجد.
     * <p>
     * ووردبريس يربط المقال بمعرّف رقمي لا باسم، بخلاف وسوم Blogger التي هي نص.
     * والإنشاء عند الغياب مقصود: إعادة محاولة مقال قديم من شاشة السجلات تحمل اسم
     * التصنيف وقد يكون حُذف من الموقع بعدها.
     */
    public int findOrCreateCategoryId(String siteUrl, String username, String applicationPassword, String name)
            throws IOException {
        return findOrCreateTermId(siteUrl, username, applicationPassword, "categories", name);
    }

    /**
     * معرّف الوسم بالاسم، ويُنشأ إن لم يوجد — بالمنطق نفسه الذي للتصنيفات.
     */
    public int findOrCreateTagId(String siteUrl, String username, String applicationPassword, String name)
            throws IOException {
        return findOrCreateTermId(siteUrl, username, applicationPassword, "tags", name);
    }

    /**
     * @param taxonomy {@code "categories"} أو {@code "tags"} كما في مسار REST
     */
    private int findOrCreateTermId(String siteUrl, String username, String applicationPassword,
                                   String taxonomy, String name) throws IOException {
        HttpUrl searchUrl = endpoint(siteUrl, "wp-json/wp/v2/" + taxonomy).newBuilder()
                .addQueryParameter("search", name)
                .addQueryParameter("per_page", "100")
                .addQueryParameter("hide_empty", "false")
                .build();

        Request search = new Request.Builder()
                .url(searchUrl)
                .header("Authorization", basicAuthHeader(username, applicationPassword))
                .get()
                .build();

        try (Response response = client.newCall(search).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("تعذر البحث في تصنيفات ووردبريس (" + response.code() + "): " + extractError(body));
            }
            // البحث في ووردبريس جزئي، فقد يعيد "أخبار محلية" عند البحث عن "أخبار"
            Integer exact = exactMatch(JsonParser.parseString(body).getAsJsonArray(), name);
            if (exact != null) {
                return exact;
            }
        }

        return createTerm(siteUrl, username, applicationPassword, taxonomy, name);
    }

    private static Integer exactMatch(JsonArray categories, String name) {
        for (JsonElement element : categories) {
            JsonObject category = element.getAsJsonObject();
            if (category.has("name") && category.has("id")
                    && equalsNormalised(category.get("name").getAsString(), name)) {
                return category.get("id").getAsInt();
            }
        }
        return null;
    }

    private static boolean equalsNormalised(String a, String b) {
        return a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }

    private int createTerm(String siteUrl, String username, String applicationPassword,
                           String taxonomy, String name) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);

        Request request = new Request.Builder()
                .url(endpoint(siteUrl, "wp-json/wp/v2/" + taxonomy))
                .header("Authorization", basicAuthHeader(username, applicationPassword))
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                JsonObject error = asObject(responseBody);
                // ووردبريس يرفض التصنيف المكرر ويعيد معرّف الموجود داخل الخطأ
                Integer existing = duplicateTermId(error);
                if (existing != null) {
                    return existing;
                }
                throw new IOException("تعذر إنشاء تصنيف ووردبريس (" + response.code() + "): "
                        + extractError(responseBody));
            }

            JsonObject created = JsonParser.parseString(responseBody).getAsJsonObject();
            return created.get("id").getAsInt();
        }
    }

    /**
     * {@code term_exists} يأتي مع معرّف التصنيف القائم في {@code data.term_id}؛
     * استخدامه أفضل من الفشل على سباق بين دفعتين تُنشئان التصنيف نفسه.
     */
    private static Integer duplicateTermId(JsonObject error) {
        if (error == null || !error.has("code") || !"term_exists".equals(error.get("code").getAsString())) {
            return null;
        }
        if (error.has("data") && error.get("data").isJsonObject()) {
            JsonObject data = error.getAsJsonObject("data");
            if (data.has("term_id")) {
                return data.get("term_id").getAsInt();
            }
        }
        return null;
    }

    private static JsonObject asObject(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * ينزّل صورة الرأس ويرفعها إلى مكتبة وسائط الموقع.
     * <p>
     * ووردبريس يقبل الرفع كجسم خام مع {@code Content-Disposition} يحمل اسم الملف،
     * لا كنموذج متعدد الأجزاء. واسم الملف مهم: منه يشتق ووردبريس صيغة الملف،
     * ورفض الامتداد المجهول أسهل عليه من تخمين النوع.
     *
     * @param imageUrl رابط الصورة كما أعاده مزود الصور
     * @return معرّف الوسيط المرفوع
     */
    public int uploadMedia(String siteUrl, String username, String applicationPassword,
                            String imageUrl, String title) throws IOException {
        DownloadedImage image = download(imageUrl);

        Request request = new Request.Builder()
                .url(endpoint(siteUrl, "wp-json/wp/v2/media"))
                .header("Authorization", basicAuthHeader(username, applicationPassword))
                .header("Content-Disposition", "attachment; filename=\"" + image.fileName() + "\"")
                .post(RequestBody.create(image.bytes(), MediaType.parse(image.contentType())))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("تعذر رفع الصورة إلى ووردبريس (" + response.code() + "): "
                        + extractError(body));
            }
            JsonObject created = JsonParser.parseString(body).getAsJsonObject();
            int id = created.get("id").getAsInt();
            log.debug("Uploaded media {} for '{}'", id, title);
            return id;
        }
    }

    /**
     * يضبط النص البديل لصورة مرفوعة.
     * <p>
     * نداء منفصل لأن الرفع جسم خام لا يحمل حقولًا أخرى.
     */
    public void setMediaAltText(String siteUrl, String username, String applicationPassword,
                                int mediaId, String altText) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("alt_text", altText);

        Request request = new Request.Builder()
                .url(endpoint(siteUrl, "wp-json/wp/v2/media/" + mediaId))
                .header("Authorization", basicAuthHeader(username, applicationPassword))
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new IOException("تعذر ضبط النص البديل للصورة (" + response.code() + "): "
                        + extractError(responseBody));
            }
        }
    }

    record DownloadedImage(byte[] bytes, String contentType, String fileName) {
    }

    private DownloadedImage download(String imageUrl) throws IOException {
        if (imageUrl != null && imageUrl.startsWith("data:")) {
            return decodeDataUri(imageUrl);
        }
        HttpUrl url = imageUrl == null ? null : HttpUrl.parse(imageUrl.trim());
        if (url == null) {
            throw new IOException("رابط الصورة غير صالح: " + imageUrl);
        }

        try (Response response = client.newCall(new Request.Builder().url(url).get().build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("تعذر تنزيل الصورة (" + response.code() + "): " + imageUrl);
            }
            byte[] bytes = response.body().bytes();
            if (bytes.length == 0) {
                throw new IOException("الصورة المنزّلة فارغة: " + imageUrl);
            }
            String contentType = response.header("Content-Type", "image/jpeg");
            // روابط مزودي الصور تحمل استعلامات طويلة ولا تنتهي بامتداد،
            // فنشتق الامتداد من نوع المحتوى لا من الرابط
            return new DownloadedImage(bytes, contentType, fileNameFor(contentType));
        }
    }

    /**
     * صورة مولَّدة تصل بياناتٍ لا رابطًا ({@code data:image/jpeg;base64,...})،
     * فلا تنزيل: تُفك وتُرفع مباشرة.
     */
    static DownloadedImage decodeDataUri(String dataUri) throws IOException {
        int comma = dataUri.indexOf(',');
        String header = comma > 5 ? dataUri.substring(5, comma) : "";
        if (comma < 0 || !header.endsWith(";base64")) {
            throw new IOException("صيغة صورة غير مدعومة (data URI بلا base64)");
        }
        String contentType = header.substring(0, header.length() - ";base64".length());
        if (contentType.isBlank()) {
            contentType = "image/png";
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUri.substring(comma + 1).trim());
        } catch (IllegalArgumentException e) {
            throw new IOException("بيانات الصورة تالفة", e);
        }
        if (bytes.length == 0) {
            throw new IOException("بيانات الصورة فارغة");
        }
        return new DownloadedImage(bytes, contentType, fileNameFor(contentType));
    }

    private static String fileNameFor(String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String extension = type.contains("png") ? "png"
                : type.contains("webp") ? "webp"
                : type.contains("gif") ? "gif"
                : "jpg";
        return "header-" + System.currentTimeMillis() + "." + extension;
    }

    private static String basicAuthHeader(String username, String applicationPassword) {
        String credentials = username + ":" + applicationPassword.replace(" ", "");
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static String extractError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "لا توجد رسالة خطأ في الاستجابة";
        }
        try {
            JsonObject obj = JsonParser.parseString(responseBody).getAsJsonObject();
            if (obj.has("message")) {
                return obj.get("message").getAsString();
            }
            return responseBody;
        } catch (Exception ignored) {
            return responseBody;
        }
    }
}
