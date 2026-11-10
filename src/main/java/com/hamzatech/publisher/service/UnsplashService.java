package com.hamzatech.publisher.service;

import com.hamzatech.publisher.http.HttpClients;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * خدمة Unsplash للحصول على صور عالية الجودة مجاناً
 */
@Log4j2
public class UnsplashService {

    private static final String API_URL = "https://api.unsplash.com/search/photos";
    /** أقصى ما يسمح به Unsplash في صفحة واحدة؛ يكفي لمدونة تنشر كثيرًا في موضوع واحد. */
    private static final int RESULTS_PER_SEARCH = 30;
    private final String accessKey;
    private final OkHttpClient client;

    public UnsplashService(String accessKey) {
        this.accessKey = normalizeAccessKey(accessKey);
        this.client = HttpClients.standard();
    }

    /**
     * البحث عن صورة مناسبة للمقال
     * @param query مصطلح البحث (عادة عنوان المقال أو كلمة مفتاحية)
     * @return رابط الصورة بدقة عالية
     */
    public String searchImage(String query) throws IOException {
        return searchImage(query, Set.of());
    }

    /**
     * مثل {@link #searchImage(String)} لكنه يتجنّب صورًا سبق نشرها.
     * <p>
     * العناوين المتشابهة ("أماكن سياحية في مصر"، "رحلة إلى مصر") كانت تحصل على
     * أول نتيجة نفسها، فتظهر كل مقالات المدونة بنفس الصورة. يُطلب الآن عدد من
     * النتائج وتؤخذ أول صورة غير مستخدمة.
     *
     * @param avoid مفاتيح الصور المستخدمة ({@link UsedImages#key})
     */
    public String searchImage(String query, Set<String> avoid) throws IOException {
        // ترميز النص للبحث
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        // بناء الطلب
        String url = API_URL + "?query=" + encodedQuery + "&per_page=" + RESULTS_PER_SEARCH + "&orientation=landscape";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Client-ID " + accessKey)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = (response.body() != null) ? response.body().string() : "";

            if (!response.isSuccessful()) {
                // 401/403 غالباً مشكلة مفتاح/صلاحيات. لا توقف خط النشر بالكامل: استخدم صورة افتراضية.
                if (response.code() == 401 || response.code() == 403) {
                    return getDefaultImage();
                }
                throw new IOException("خطأ من Unsplash: " + response.code() + " - " + extractUnsplashError(responseBody));
            }

            // استخراج رابط الصورة من النتيجة الأولى
            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray results = jsonObject.getAsJsonArray("results");

            if (results == null || results.size() == 0) {
                // لا توجد نتائج، نرجع صورة افتراضية
                return getDefaultImage();
            }

            return pickUnused(results, avoid);
        }
    }

    /**
     * أول صورة لم تُستخدم، أو الأولى إن كانت كلها مستخدمة — صورة مكررة أفضل من
     * مقال بلا صورة.
     */
    static String pickUnused(JsonArray results, Set<String> avoid) {
        String first = null;
        for (JsonElement result : results) {
            // نستخدم "regular" كحجم متوسط (1080px)
            String image = result.getAsJsonObject().getAsJsonObject("urls").get("regular").getAsString();
            if (first == null) {
                first = image;
            }
            if (!avoid.contains(UsedImages.key(image))) {
                return image;
            }
        }
        log.info("All {} Unsplash results were already used on this blog; reusing the top one.", results.size());
        return first;
    }

    /**
     * الحصول على صورة عشوائية حسب موضوع معين
     */
    public String getRandomImage(String topic) throws IOException {
        String url = "https://api.unsplash.com/photos/random?query=" +
                URLEncoder.encode(topic, StandardCharsets.UTF_8) +
                "&orientation=landscape";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Client-ID " + accessKey)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = (response.body() != null) ? response.body().string() : "";

            if (!response.isSuccessful()) {
                if (response.code() == 401 || response.code() == 403) {
                    return getDefaultImage();
                }
                return getDefaultImage();
            }

            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonObject urls = jsonObject.getAsJsonObject("urls");

            return urls.get("regular").getAsString();
        }
    }

    private static String normalizeAccessKey(String raw) {
        if (raw == null) return "";
        String key = raw.trim();

        // لو المستخدم لصق "Client-ID xxx" بالخطأ في الإعدادات
        if (key.regionMatches(true, 0, "Client-ID", 0, "Client-ID".length())) {
            key = key.substring("Client-ID".length()).trim();
        }

        // إزالة علامات الاقتباس إن وُجدت
        if ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'"))) {
            key = key.substring(1, key.length() - 1).trim();
        }
        return key;
    }

    private static String extractUnsplashError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "لا توجد رسالة خطأ في الاستجابة (تحقق من مفتاح Unsplash).";
        }
        try {
            JsonObject obj = JsonParser.parseString(responseBody).getAsJsonObject();
            if (obj.has("errors")) {
                return obj.get("errors").toString();
            }
            if (obj.has("error")) {
                return obj.get("error").getAsString();
            }
            return responseBody;
        } catch (Exception ignored) {
            return responseBody;
        }
    }

    /**
     * صورة افتراضية في حالة عدم وجود نتائج
     */
    private String getDefaultImage() {
        return "https://images.unsplash.com/photo-1499750310107-5fef28a66643?w=1080";
    }
}
