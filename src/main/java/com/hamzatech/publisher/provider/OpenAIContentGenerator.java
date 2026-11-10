package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.service.ConfigService;
import com.hamzatech.publisher.service.OpenAIService;

import java.io.IOException;

/**
 * توليد المقالات عبر OpenAI.
 * <p>
 * العميل يُنشأ عند أول استخدام لا في المُنشئ، حتى لا يُطلب المفتاح من مزود
 * غير مختار، ويُعاد إنشاؤه إذا تغيّر المفتاح في الإعدادات.
 */
public class OpenAIContentGenerator implements ContentGenerator {

    public static final String ID = "openai";

    private final ConfigService configService = ConfigService.getInstance();
    private OpenAIService client;
    private String clientKey;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "ChatGPT (OpenAI)";
    }

    @Override
    public boolean isConfigured() {
        String key = configService.getOpenAIKey();
        return key != null && !key.isBlank();
    }

    @Override
    public String generateArticle(String title, String targetLanguage) throws IOException {
        return client().generateArticle(title, targetLanguage, configService.getOpenAIModel());
    }

    @Override
    public String complete(String prompt) throws IOException {
        return client().complete(prompt, configService.getOpenAIModel());
    }

    private synchronized OpenAIService client() {
        String key = configService.getOpenAIKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("مفتاح OpenAI غير موجود. الرجاء إضافته في الإعدادات.");
        }
        if (client == null || !key.equals(clientKey)) {
            client = new OpenAIService(key);
            clientKey = key;
        }
        return client;
    }
}
