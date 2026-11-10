package com.hamzatech.publisher.service;

import com.hamzatech.publisher.http.HttpClients;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * خدمة ترجمة النصوص العربية إلى الإنجليزية باستخدام AI
 */
@Log4j2
public class TranslationService {

    private static final List<String> NON_TEXT_MODEL_HINTS = List.of(
            "image", "imagen", "tts", "audio", "veo", "vision", "embedding", "aqa", "live"
    );

    private final ConfigService configService = ConfigService.getInstance();
    private final OkHttpClient httpClient;
    private final Gson gson;
    private String cachedGeminiModel = null;
    private String cachedGeminiModelKey = null;
    private ClaudeService claudeService;
    private String claudeServiceKey;

    private static final String TRANSLATION_PROMPT =
            "Translate this Arabic text to English. Only provide the translation, no explanations:\n\n";

    public TranslationService() {
        this.httpClient = HttpClients.standard();
        this.gson = new Gson();
    }

    /**
     * ترجمة النص من العربية إلى الإنجليزية باستخدام AI
     * مناسبة لإنشاء روابط SEO
     */
    public String translateToEnglish(String arabicText) throws IOException {
        if (arabicText == null || arabicText.trim().isEmpty()) {
            return "";
        }

        String aiProvider = configService.getAIProvider();
        log.debug("Translating text using: {}", aiProvider);

        return switch (aiProvider) {
            case "gemini" -> translateWithGemini(arabicText);
            case "claude" -> translateWithClaude(arabicText);
            default -> translateWithOpenAI(arabicText);
        };
    }

    /**
     * ترجمة باستخدام Claude
     */
    private String translateWithClaude(String text) throws IOException {
        String apiKey = configService.getClaudeKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IOException("Claude API key not found");
        }
        if (claudeService == null || !apiKey.equals(claudeServiceKey)) {
            claudeService = new ClaudeService(apiKey);
            claudeServiceKey = apiKey;
        }
        return claudeService.complete(TRANSLATION_PROMPT + text, configService.getClaudeModel(), ClaudeService.SHORT_REPLY_MAX_TOKENS);
    }

    /**
     * ترجمة باستخدام Gemini
     */
    private String translateWithGemini(String text) throws IOException {
        String apiKey = configService.getGeminiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IOException("Gemini API key not found");
        }

        // النموذج المحدد في الإعدادات أولًا، وإلا موديل متاح يُكتشف ديناميكياً
        // (مع إعادة الحل إذا تغير المفتاح)
        String configuredModel = configService.getGeminiModel();
        if (configuredModel != null) {
            cachedGeminiModel = configuredModel;
            cachedGeminiModelKey = null;
        } else if (cachedGeminiModel == null || !apiKey.equals(cachedGeminiModelKey)) {
            cachedGeminiModel = resolveGeminiModel(apiKey);
            cachedGeminiModelKey = apiKey;
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + cachedGeminiModel + ":generateContent?key=" + apiKey;

        String prompt = TRANSLATION_PROMPT + text;

        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);

        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject content = new JsonObject();
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.1);
        generationConfig.addProperty("maxOutputTokens", 1000);

        JsonObject payload = new JsonObject();
        payload.add("contents", contents);
        payload.add("generationConfig", generationConfig);

        String jsonBody = gson.toJson(payload);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Gemini API error: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            return extractGeminiResponse(responseBody);
        }
    }

    /**
     * الحصول على موديل Gemini متاح
     */
    private String resolveGeminiModel(String apiKey) throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to list Gemini models: " + response.code());
            }

            String body = response.body().string();
            ListModelsResponse models = gson.fromJson(body, ListModelsResponse.class);

            if (models == null || models.models == null || models.models.isEmpty()) {
                throw new IOException("No Gemini models available");
            }

            // البحث عن موديل نصي يدعم generateContent
            Model flashModel = null;
            Model geminiModel = null;
            Model anyModel = null;

            for (Model m : models.models) {
                if (m == null || m.name == null || m.supportedGenerationMethods == null) continue;

                boolean supportsGenerate = false;
                for (String method : m.supportedGenerationMethods) {
                    if ("generateContent".equals(method)) {
                        supportsGenerate = true;
                        break;
                    }
                }

                if (!supportsGenerate) continue;

                String nameLower = m.name.toLowerCase(Locale.ROOT);

                // استبعاد الموديلات غير النصية (صور/صوت/تضمين) رغم دعمها لـ generateContent
                if (isNonTextModel(nameLower)) {
                    log.debug("Skipping non-text model: {}", m.name);
                    continue;
                }

                boolean isGemini = nameLower.contains("gemini");
                boolean isFlash = nameLower.contains("flash");

                if (isGemini && isFlash) {
                    flashModel = m;
                    break;
                }
                if (isGemini && geminiModel == null) {
                    geminiModel = m;
                }
                if (anyModel == null) {
                    anyModel = m;
                }
            }

            Model best = flashModel != null ? flashModel : (geminiModel != null ? geminiModel : anyModel);

            if (best == null) {
                throw new IOException("No suitable Gemini model found");
            }

            // إزالة "models/" من الاسم إذا كان موجوداً
            String modelName = best.name.startsWith("models/") ? best.name.substring("models/".length()) : best.name;
            log.info("Using Gemini model for translation: {}", modelName);
            return modelName;
        }
    }

    /**
     * موديلات لا تُرجع نصاً في أول جزء من الرد (صور، صوت، تضمين...)
     */
    private boolean isNonTextModel(String nameLower) {
        for (String hint : NON_TEXT_MODEL_HINTS) {
            if (nameLower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ترجمة باستخدام OpenAI
     */
    private String translateWithOpenAI(String text) throws IOException {
        String apiKey = configService.getOpenAIKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IOException("OpenAI API key not found");
        }

        String url = "https://api.openai.com/v1/chat/completions";

        String prompt = TRANSLATION_PROMPT + text;

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", configService.getOpenAIModel());
        payload.add("messages", messages);
        // max_completion_tokens بدل max_tokens، وبلا temperature: نماذج OpenAI
        // الأحدث ترفض الحقلين القديمين. الحد واسع لأن النماذج المفكّرة تحسب
        // تفكيرها منه
        payload.addProperty("max_completion_tokens", 1000);

        String jsonBody = gson.toJson(payload);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("OpenAI API error: " + response.code());
            }

            String responseBody = response.body().string();
            return extractOpenAIResponse(responseBody);
        }
    }

    /**
     * استخراج النص من رد Gemini
     */
    private String extractGeminiResponse(String json) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            return jsonObject
                    .getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString()
                    .trim();
        } catch (Exception e) {
            log.error("Failed to parse Gemini response", e);
            return "";
        }
    }

    /**
     * استخراج النص من رد OpenAI
     */
    private String extractOpenAIResponse(String json) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            return jsonObject
                    .getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString()
                    .trim();
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response", e);
            return "";
        }
    }

    // DTOs للتعامل مع Gemini API
    static class ListModelsResponse {
        List<Model> models;
    }

    static class Model {
        String name;
        List<String> supportedGenerationMethods;
    }
}
