package com.hamzatech.publisher.service;

import com.hamzatech.publisher.util.AppPaths;
import com.hamzatech.publisher.util.SecretStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigServiceTest {

    private final ConfigService configService = ConfigService.getInstance();

    @AfterEach
    void resetKnownKeys() {
        configService.set(ConfigService.PUBLISH_DELAY, null);
        configService.set(ConfigService.SLUG_TYPE, null);
        configService.set(ConfigService.OPENAI_IMAGE_QUALITY, null);
    }

    @Test
    void openAIImageQuality_defaultsToMediumWhenUnset() {
        assertEquals("medium", configService.getOpenAIImageQuality());
    }

    @Test
    void openAIImageQuality_returnsStoredValue() {
        configService.set(ConfigService.OPENAI_IMAGE_QUALITY, "low");
        assertEquals("low", configService.getOpenAIImageQuality());
    }

    @Test
    void openAIImageQuality_fallsBackToMediumOnUnknownValue() {
        // "hd" قيمة dall-e-3؛ إرسالها لنموذج gpt-image يُفشل الطلب كله
        configService.set(ConfigService.OPENAI_IMAGE_QUALITY, "hd");
        assertEquals("medium", configService.getOpenAIImageQuality());
    }

    @Test
    void getSet_roundTripsAndNullRemovesKey() {
        configService.set("some.test.key", "value");
        assertEquals("value", configService.get("some.test.key"));

        configService.set("some.test.key", null);
        assertNull(configService.get("some.test.key"));
    }

    @Test
    void getPublishDelay_defaultsToFiveWhenUnset() {
        assertEquals(5, configService.getPublishDelay());
    }

    @Test
    void getPublishDelay_returnsStoredValue() {
        configService.setPublishDelay(42);
        assertEquals(42, configService.getPublishDelay());
    }

    @Test
    void getPublishDelay_fallsBackToDefaultOnInvalidValue() {
        configService.set(ConfigService.PUBLISH_DELAY, "not-a-number");
        assertEquals(5, configService.getPublishDelay());
    }

    @Test
    void slugType_defaultsToAutoWithBothFlagsOff() {
        assertEquals("auto", configService.getSlugType());
        assertFalse(configService.useCustomSlug());
        assertFalse(configService.useEnglishSlug());
    }

    @Test
    void slugType_customEnablesCustomSlugOnly() {
        configService.setSlugType("custom");
        assertTrue(configService.useCustomSlug());
        assertFalse(configService.useEnglishSlug());
    }

    @Test
    void slugType_englishEnablesBothFlags() {
        configService.setSlugType("english");
        assertTrue(configService.useCustomSlug());
        assertTrue(configService.useEnglishSlug());
    }

    @Test
    void hasKey_isFalseForBlankValues() {
        configService.set("blank.key", "   ");
        assertFalse(configService.hasKey("blank.key"));

        configService.set("blank.key", "x");
        assertTrue(configService.hasKey("blank.key"));

        configService.set("blank.key", null);
    }

    @Test
    void saveConfig_writesApiKeysEncrypted_neverInPlainText() throws IOException {
        String openAi = "sk-plain-openai-value";
        String gemini = "AIza-plain-gemini-value";
        String unsplash = "plain-unsplash-value";

        configService.set(ConfigService.OPENAI_API_KEY, openAi);
        configService.set(ConfigService.GEMINI_API_KEY, gemini);
        configService.set(ConfigService.UNSPLASH_API_KEY, unsplash);
        try {
            configService.saveConfig();

            Path file = AppPaths.dataDir().resolve("config.properties");
            String raw = Files.readString(file);
            assertFalse(raw.contains(openAi), "the OpenAI key must not appear in the file");
            assertFalse(raw.contains(gemini), "the Gemini key must not appear in the file");
            assertFalse(raw.contains(unsplash), "the Unsplash key must not appear in the file");

            Properties onDisk = new Properties();
            try (var in = Files.newInputStream(file)) {
                onDisk.load(in);
            }
            assertTrue(SecretStore.isProtected(onDisk.getProperty(ConfigService.OPENAI_API_KEY)));
            assertEquals(openAi, SecretStore.reveal(onDisk.getProperty(ConfigService.OPENAI_API_KEY)));

            // القيم في الذاكرة تبقى صريحة لأن التطبيق يحتاجها لاستدعاء الخدمات
            assertEquals(openAi, configService.get(ConfigService.OPENAI_API_KEY));
        } finally {
            configService.set(ConfigService.OPENAI_API_KEY, null);
            configService.set(ConfigService.GEMINI_API_KEY, null);
            configService.set(ConfigService.UNSPLASH_API_KEY, null);
            configService.saveConfig();
        }
    }

    @Test
    void saveConfig_leavesNonSecretSettingsReadable() throws IOException {
        configService.setPublishDelay(42);
        configService.setSlugType("english");
        configService.saveConfig();

        Properties onDisk = new Properties();
        try (var in = Files.newInputStream(AppPaths.dataDir().resolve("config.properties"))) {
            onDisk.load(in);
        }
        assertEquals("42", onDisk.getProperty(ConfigService.PUBLISH_DELAY));
        assertEquals("english", onDisk.getProperty(ConfigService.SLUG_TYPE));
    }
}
