package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.service.ClaudeService;
import com.hamzatech.publisher.service.ConfigService;

import java.io.IOException;

/**
 * توليد المقالات عبر Claude (Anthropic).
 *
 * @see OpenAIContentGenerator لسبب التهيئة الكسولة
 */
public class ClaudeContentGenerator implements ContentGenerator {

    public static final String ID = "claude";

    private final ConfigService configService = ConfigService.getInstance();
    private ClaudeService client;
    private String clientKey;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Claude (Anthropic)";
    }

    @Override
    public boolean isConfigured() {
        String key = configService.getClaudeKey();
        return key != null && !key.isBlank();
    }

    @Override
    public String generateArticle(String title, String targetLanguage) throws IOException {
        return client().generateArticle(title, targetLanguage, configService.getClaudeModel());
    }

    @Override
    public String complete(String prompt) throws IOException {
        return client().complete(prompt, configService.getClaudeModel(), ClaudeService.SHORT_REPLY_MAX_TOKENS);
    }

    private synchronized ClaudeService client() {
        String key = configService.getClaudeKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("مفتاح Claude غير موجود. الرجاء إضافته في الإعدادات.");
        }
        if (client == null || !key.equals(clientKey)) {
            client = new ClaudeService(key);
            clientKey = key;
        }
        return client;
    }
}
