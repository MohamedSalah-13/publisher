package com.hamzatech.publisher.service;

import com.hamzatech.publisher.http.HttpClients;
import com.hamzatech.publisher.util.HtmlCleaner;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OpenAIService {

    private static final String BASE_URL = "https://api.openai.com/v1";
    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient client;
    private final Gson gson;

    // نستقبل المفتاح في الكونستركتور (سيأتي من الإعدادات لاحقاً)
    public OpenAIService(String apiKey) {
        this(apiKey, BASE_URL);
    }

    /**
     * @param baseUrl أصل الواجهة؛ يُمرَّر في الاختبارات لتوجيه الطلبات إلى خادم محلي
     */
    OpenAIService(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.gson = new Gson();

        // إعدادات المهلة (Timeout) لأن كتابة المقال تأخذ وقتاً
        this.client = HttpClients.longRunning();
    }

    /**
     * يولّد صورة ويعيدها مضمّنة كـ {@code data:} URI لا كرابط.
     * <p>
     * روابط صور OpenAI تنتهي صلاحيتها بعد ساعة تقريبًا، فالمقال الذي يحمل رابطها
     * يفقد صورته بعدها. البيانات نفسها لا تنتهي: ووردبريس يرفعها إلى مكتبة
     * وسائطه، ونافذة المعاينة تعرضها مباشرة.
     *
     * @param model   {@code gpt-image-*} (الافتراضي) أو {@code dall-e-*} — لكل عائلة
     *                معاملاتها، وكلاهما يعيد البيانات لا رابطًا
     * @param quality {@code low} أو {@code medium} أو {@code high}؛ تُتجاهل مع
     *                {@code dall-e-*} التي لا تعرف هذه القيم
     * @return {@code data:image/<type>;base64,...}
     */
    public String generateImage(String prompt, String model, String quality) throws IOException {
        JsonObject requestPayload = new JsonObject();
        requestPayload.addProperty("model", model);
        requestPayload.addProperty("prompt", prompt);
        requestPayload.addProperty("n", 1);
        String mimeType;
        if (model.toLowerCase(java.util.Locale.ROOT).startsWith("dall-e")) {
            requestPayload.addProperty("size", model.contains("3") ? "1792x1024" : "1024x1024");
            requestPayload.addProperty("response_format", "b64_json");
            mimeType = "image/png";
        } else {
            // صورة عريضة وJPEG مضغوط: حجمها يبقى بضع مئات من الكيلوبايت بدل عدة
            // ميغابايت. الجودة من الإعدادات لأنها ما يحدد التكلفة أكثر من غيرها
            requestPayload.addProperty("size", "1536x1024");
            requestPayload.addProperty("quality", quality);
            requestPayload.addProperty("output_format", "jpeg");
            requestPayload.addProperty("output_compression", 85);
            mimeType = "image/jpeg";
        }

        RequestBody body = RequestBody.create(gson.toJson(requestPayload),
                MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(baseUrl + "/images/generations")
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("خطأ من OpenAI Images (" + model + "): " + response.code()
                        + " | التفاصيل: " + responseBody);
            }
            try {
                JsonArray data = JsonParser.parseString(responseBody).getAsJsonObject().getAsJsonArray("data");
                if (data == null || data.isEmpty() || !data.get(0).getAsJsonObject().has("b64_json")) {
                    throw new IOException("رد OpenAI Images لا يحتوي على صورة. الرد: " + responseBody);
                }
                String base64 = data.get(0).getAsJsonObject().get("b64_json").getAsString();
                return "data:" + mimeType + ";base64," + base64;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("تعذر قراءة رد OpenAI Images. الرد: " + responseBody, e);
            }
        }
    }

    /**
     * الدالة الرئيسية لتوليد المقال
     */
    public String generateArticle(String title) throws IOException {
        return generateArticle(title, "arabic");
    }

    public String generateArticle(String title, String targetLanguage) throws IOException {
        return generateArticle(title, targetLanguage, ConfigService.getInstance().getOpenAIModel());
    }

    /**
     * @param model اسم النموذج، مثل {@code gpt-4.1-mini}
     */
    public String generateArticle(String title, String targetLanguage, String model) throws IOException {
        // 1. تجهيز الرسائل من الإعدادات المشتركة لكل المزودين
        ArticlePrompt prompt = ArticlePrompt.forArticle(title, targetLanguage);
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", prompt.system()));
        messages.add(new Message("user", prompt.user()));

        // 2. تجهيز الطلب (Request Body)
        ChatRequest requestPayload = new ChatRequest(model, messages);
        String jsonBody = gson.toJson(requestPayload);

        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

        // 3. بناء طلب الـ HTTP
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        // 4. التنفيذ واستقبال الرد
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("خطأ من OpenAI (" + model + "): " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();

            // 5. استخراج النص من الـ JSON المعقد
            return HtmlCleaner.clean(extractContentFromResponse(responseBody));
        }
    }

    /**
     * طلب نصي قصير عام (بيانات SEO مثلًا).
     */
    public String complete(String prompt, String model) throws IOException {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("user", prompt));
        RequestBody body = RequestBody.create(gson.toJson(new ChatRequest(model, messages)),
                MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("خطأ من OpenAI (" + model + "): " + response.code() + " - " + responseBody);
            }
            return extractContentFromResponse(responseBody).trim();
        }
    }

    private String extractContentFromResponse(String jsonResponse) {
        // تحليل الـ JSON للوصول إلى: choices[0].message.content
        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        return jsonObject.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .get("message").getAsJsonObject()
                .get("content").getAsString();
    }

    // --- DTOs (Data Transfer Objects) لترتيب الـ JSON ---

    // يمثل هيكل الطلب الكامل
    private static class ChatRequest {
        String model;
        List<Message> messages;
        // بلا temperature: نماذج OpenAI الأحدث (gpt-5 وما بعده) ترفض أي قيمة غير الافتراضية

        public ChatRequest(String model, List<Message> messages) {
            this.model = model;
            this.messages = messages;
        }
    }

    // يمثل الرسالة الواحدة
    private static class Message {
        String role; // "system" or "user"
        String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}