package com.hamzatech.publisher.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * الوسيطة الاختيارية في {@link AIService} و{@link ImageService} التي تسمح لملف
 * تعريف وجهة بتجاوز مزوّد الإعدادات العامة.
 */
class ProviderOverrideTest {

    private final ConfigService configService = ConfigService.getInstance();

    @AfterEach
    void resetConfig() {
        configService.set(ConfigService.AI_PROVIDER, null);
        configService.set(ConfigService.IMAGE_PROVIDER, null);
    }

    @Test
    void aiService_withNoOverride_usesTheGlobalSetting() {
        configService.set(ConfigService.AI_PROVIDER, "gemini");

        assertEquals("gemini", new AIService().getCurrentProvider());
    }

    @Test
    void aiService_withAnOverride_ignoresTheGlobalSetting() {
        configService.set(ConfigService.AI_PROVIDER, "gemini");

        assertEquals("openai", new AIService("openai").getCurrentProvider());
    }

    @Test
    void imageService_withNoOverride_usesTheGlobalSetting() {
        configService.set(ConfigService.IMAGE_PROVIDER, "dalle");

        assertEquals("dalle", new ImageService().getCurrentProvider());
    }

    @Test
    void imageService_withAnOverride_ignoresTheGlobalSetting() {
        configService.set(ConfigService.IMAGE_PROVIDER, "dalle");

        assertEquals("unsplash", new ImageService("unsplash").getCurrentProvider());
    }
}
