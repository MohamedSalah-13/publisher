package com.hamzatech.publisher.provider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRegistryTest {

    @AfterEach
    void restoreRegistry() {
        ProviderRegistry.resetToBuiltIns();
    }

    @Test
    void builtInContentGenerators_areRegisteredUnderTheIdsStoredInConfig() {
        assertEquals(OpenAIContentGenerator.ID, ProviderRegistry.contentGenerator("openai").id());
        assertEquals(GeminiContentGenerator.ID, ProviderRegistry.contentGenerator("gemini").id());
    }

    @Test
    void builtInImageProviders_areRegisteredUnderTheIdsStoredInConfig() {
        assertEquals(UnsplashImageProvider.ID, ProviderRegistry.imageProvider("unsplash").id());
        assertEquals(OpenAIImageProvider.ID, ProviderRegistry.imageProvider("dalle").id());
    }

    @Test
    void unknownId_fallsBackToTheFirstProviderInsteadOfFailing() {
        // يحدث إذا حمل ملف الإعدادات معرّفًا كتبته نسخة أحدث من التطبيق
        assertEquals(OpenAIContentGenerator.ID, ProviderRegistry.contentGenerator("claude-4").id());
        assertEquals(OpenAIContentGenerator.ID, ProviderRegistry.contentGenerator(null).id());
        assertEquals(UnsplashImageProvider.ID, ProviderRegistry.imageProvider("midjourney").id());
        assertEquals(UnsplashImageProvider.ID, ProviderRegistry.imageProvider(null).id());
    }

    @Test
    void lookupReturnsTheSameInstance_soCachedHttpClientsAreReused() {
        assertSame(ProviderRegistry.contentGenerator("gemini"), ProviderRegistry.contentGenerator("gemini"));
        assertSame(ProviderRegistry.imageProvider("dalle"), ProviderRegistry.imageProvider("dalle"));
    }

    @Test
    void listingProviders_keepsRegistrationOrder_forTheSettingsScreen() {
        assertEquals(List.of("openai", "gemini", "claude"),
                ProviderRegistry.contentGenerators().stream().map(ContentGenerator::id).toList());
        assertEquals(List.of("unsplash", "dalle"),
                ProviderRegistry.imageProviders().stream().map(ImageProvider::id).toList());
    }

    @Test
    void everyProviderExposesADisplayName() {
        ProviderRegistry.contentGenerators()
                .forEach(g -> assertTrue(g.displayName() != null && !g.displayName().isBlank(), g.id()));
        ProviderRegistry.imageProviders()
                .forEach(p -> assertTrue(p.displayName() != null && !p.displayName().isBlank(), p.id()));
    }

    @Test
    void aThirdProviderIsJustAClassPlusARegistration() {
        // الغرض من كل هذا: إضافة مزود لا تتطلب تعديل أي كلاس قائم
        ContentGenerator custom = new ContentGenerator() {
            public String id() {
                return "test-provider";
            }

            public String displayName() {
                return "Test Provider";
            }

            public boolean isConfigured() {
                return true;
            }

            public String generateArticle(String title, String targetLanguage) throws IOException {
                return "article about " + title + " in " + targetLanguage;
            }
        };

        ProviderRegistry.register(custom);
        try {
            assertSame(custom, ProviderRegistry.contentGenerator("test-provider"));
            assertTrue(ProviderRegistry.contentGenerators().contains(custom));
        } finally {
            ProviderRegistry.resetToBuiltIns();
        }
    }
}
