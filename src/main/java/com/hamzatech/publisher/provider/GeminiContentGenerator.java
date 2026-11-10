package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.service.ConfigService;
import com.hamzatech.publisher.service.GeminiService;

import java.io.IOException;

/**
 * توليد المقالات عبر Gemini.
 *
 * @see OpenAIContentGenerator لسبب التهيئة الكسولة
 */
public class GeminiContentGenerator implements ContentGenerator {

    public static final String ID = "gemini";

    private final ConfigService configService = ConfigService.getInstance();
    private GeminiService client;
    private String clientKey;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Gemini (Google)";
    }

    @Override
    public boolean isConfigured() {
        String key = configService.getGeminiKey();
        return key != null && !key.isBlank();
    }

    @Override
    public String generateArticle(String title, String targetLanguage) throws IOException {
        return client().generateArticle(title, targetLanguage, configService.getGeminiModel());
    }

    @Override
    public String complete(String prompt) throws IOException {
        return client().complete(prompt, configService.getGeminiModel());
    }

    private synchronized GeminiService client() {
        String key = configService.getGeminiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("مفتاح Gemini غير موجود. الرجاء إضافته في الإعدادات.");
        }
        if (client == null || !key.equals(clientKey)) {
            client = new GeminiService(key);
            clientKey = key;
        }
        return client;
    }
}
