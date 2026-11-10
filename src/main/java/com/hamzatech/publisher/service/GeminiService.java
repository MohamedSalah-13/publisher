package com.hamzatech.publisher.service;

import com.hamzatech.publisher.http.HttpClients;
import com.hamzatech.publisher.util.HtmlCleaner;
import com.google.gson.Gson;
import okhttp3.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GeminiService {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final List<String> API_VERSIONS_TO_TRY = List.of("v1beta", "v1");
    private static final List<String> NON_TEXT_OR_UNSTABLE_HINTS = List.of(
            "image", "imagen", "tts", "audio", "veo", "vision", "embedding", "aqa", "live",
            "preview", "exp", "thinking", "learnlm", "gemma");

    private final String apiKey;
    private final OkHttpClient client;
    private final Gson gson;

    public GeminiService(String apiKey) {
        this.apiKey = apiKey;
        this.gson = new Gson();
        this.client = HttpClients.longRunning();
    }

    public String generateArticle(String title) throws IOException {
        return generateArticle(title, "arabic");
    }

    public String generateArticle(String title, String targetLanguage) throws IOException {
        return generateArticle(title, targetLanguage, ConfigService.getInstance().getGeminiModel());
    }

    /**
     * @param model اسم النموذج (مثل {@code gemini-2.5-flash})، أو {@code null}
     *              لاكتشاف نموذج متاح تلقائيًا
     */
    public String generateArticle(String title, String targetLanguage, String model) throws IOException {
        GeminiRequest requestObj = buildRequest(title, targetLanguage);
        String jsonBody = gson.toJson(requestObj);

        IOException last = null;

        for (String apiVersion : API_VERSIONS_TO_TRY) {
            try {
                String resolved = model != null ? model : resolveModelForGenerateContent(apiVersion);
                return callGenerateContent(apiVersion, resolved, jsonBody);
            } catch (IOException e) {
                last = e;
                // جرّب النسخة التالية
            }
        }

        throw new IOException("Gemini failed on all API versions. Last error: " + (last != null ? last.getMessage() : "<unknown>"), last);
    }

    /**
     * طلب نصي قصير عام (بيانات SEO مثلًا).
     *
     * @param model اسم النموذج، أو {@code null} للاكتشاف التلقائي
     */
    public String complete(String prompt, String model) throws IOException {
        GeminiRequest requestObj = new GeminiRequest();
        GeminiRequest.Part part = new GeminiRequest.Part();
        part.text = prompt;
        GeminiRequest.Content content = new GeminiRequest.Content();
        content.parts = Collections.singletonList(part);
        requestObj.contents = Collections.singletonList(content);
        GeminiRequest.GenerationConfig config = new GeminiRequest.GenerationConfig();
        config.temperature = 0.4;
        // واسع لأن نماذج Gemini المفكّرة تحسب تفكيرها من الحد نفسه
        config.maxOutputTokens = 4000;
        requestObj.generationConfig = config;
        String jsonBody = gson.toJson(requestObj);

        String version = API_VERSIONS_TO_TRY.getFirst();
        String resolved = model != null ? model : resolveModelForGenerateContent(version);
        return callGenerateContent(version, resolved, jsonBody).trim();
    }

    private GeminiRequest buildRequest(String title, String targetLanguage) {
        GeminiRequest requestObj = new GeminiRequest();

        // Gemini يقبل تعليمات نظام منفصلة في v1beta فقط، فنرسل الاثنين نصًّا واحدًا
        // ليعمل الطلب نفسه على النسختين
        GeminiRequest.Part part = new GeminiRequest.Part();
        part.text = ArticlePrompt.forArticle(title, targetLanguage).combined();

        GeminiRequest.Content content = new GeminiRequest.Content();
        content.parts = Collections.singletonList(part);

        requestObj.contents = Collections.singletonList(content);

        GeminiRequest.GenerationConfig config = new GeminiRequest.GenerationConfig();
        config.temperature = 0.7;
        config.maxOutputTokens = ArticlePrompt.maxOutputTokens(ConfigService.getInstance().getArticleLength());
        requestObj.generationConfig = config;

        return requestObj;
    }

    /**
     * يحل المشكلة جذريًا: لا تعتمد على اسم موديل ثابت.
     * استعلم عن الموديلات المتاحة واختر موديل يدعم generateContent (مع تفضيل flash).
     */
    private String resolveModelForGenerateContent(String apiVersion) throws IOException {
        ListModelsResponse models = listModels(apiVersion);

        if (models == null || models.models == null || models.models.isEmpty()) {
            throw new IOException("No models returned from ListModels for " + apiVersion + ". Check API key / project access.");
        }

        // تفضيل: gemini + flash + يدعم generateContent
        Model best = null;
        for (Model m : models.models) {
            if (m == null || m.name == null) continue;
            if (!supportsGenerateContent(m)) continue;

            String nameLower = m.name.toLowerCase(Locale.ROOT);
            if (!isStableTextModel(nameLower)) continue;
            boolean isGemini = nameLower.contains("gemini");
            boolean isFlash = nameLower.contains("flash");

            if (isGemini && isFlash) {
                best = m;
                break;
            }
            if (best == null && isGemini) {
                best = m;
            }
            if (best == null) {
                best = m;
            }
        }

        if (best == null) {
            throw new IOException("No available model supports generateContent on " + apiVersion + ". Your key/project likely doesn't have access.");
        }

        // name يأتي عادة بشكل: "models/...." ونحن نحتاج الجزء بعد "models/"
        return best.name.startsWith("models/") ? best.name.substring("models/".length()) : best.name;
    }

    /**
     * نماذج الصور والصوت والتضمين تدعم {@code generateContent} هي الأخرى، والنماذج
     * التجريبية قد تختفي دون إنذار؛ اختيار أيٍّ منها تلقائيًا كان يُفشل المقالات
     * أو يغيّر جودتها فجأة.
     */
    static boolean isStableTextModel(String nameLower) {
        for (String hint : NON_TEXT_OR_UNSTABLE_HINTS) {
            if (nameLower.contains(hint)) {
                return false;
            }
        }
        return true;
    }

    private boolean supportsGenerateContent(Model m) {
        if (m.supportedGenerationMethods == null) return false;
        for (String method : m.supportedGenerationMethods) {
            if ("generateContent".equals(method)) return true;
        }
        return false;
    }

    private ListModelsResponse listModels(String apiVersion) throws IOException {
        String url = BASE_URL + "/" + apiVersion + "/models?key=" + apiKey;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("ListModels failed. HTTP " + response.code()
                        + " (apiVersion=" + apiVersion + ") - Body: " + (body.isBlank() ? "<empty>" : body));
            }
            return gson.fromJson(body, ListModelsResponse.class);
        }
    }

    private String callGenerateContent(String apiVersion, String model, String jsonBody) throws IOException {
        String url = BASE_URL + "/" + apiVersion + "/models/" + model + ":generateContent?key=" + apiKey;

        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("Gemini API request failed. HTTP " + response.code()
                        + " (apiVersion=" + apiVersion + ", model=" + model + ") - Body: "
                        + (responseBody.isBlank() ? "<empty>" : responseBody));
            }

            GeminiResponse geminiResponse = gson.fromJson(responseBody, GeminiResponse.class);

            if (geminiResponse == null
                    || geminiResponse.candidates == null
                    || geminiResponse.candidates.isEmpty()
                    || geminiResponse.candidates.getFirst() == null
                    || geminiResponse.candidates.getFirst().content == null
                    || geminiResponse.candidates.getFirst().content.parts == null
                    || geminiResponse.candidates.getFirst().content.parts.isEmpty()
                    || geminiResponse.candidates.getFirst().content.parts.getFirst() == null
                    || geminiResponse.candidates.getFirst().content.parts.getFirst().text == null) {
                throw new IOException("Gemini API returned an unexpected response shape: " + responseBody);
            }

            String rawText = geminiResponse.candidates.getFirst().content.parts.getFirst().text;
            return HtmlCleaner.clean(rawText);
        }
    }

    // --- DTOs ---

    class GeminiRequest {
        List<Content> contents;
        GenerationConfig generationConfig;

        static class Content {
            List<Part> parts;
        }

        static class Part {
            String text;
        }

        static class GenerationConfig {
            double temperature;
            int maxOutputTokens;
        }
    }

    class GeminiResponse {
        List<Candidate> candidates;

        static class Candidate {
            Content content;
        }

        static class Content {
            List<Part> parts;
        }

        static class Part {
            String text;
        }
    }

    // ListModels DTOs
    static class ListModelsResponse {
        List<Model> models;
    }

    static class Model {
        String name; // مثال: "models/gemini-1.5-flash-latest"
        List<String> supportedGenerationMethods;
    }
}