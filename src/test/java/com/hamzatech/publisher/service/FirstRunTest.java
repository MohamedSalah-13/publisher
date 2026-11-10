package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.WordPressSite;
import com.hamzatech.publisher.provider.ContentGenerator;
import com.hamzatech.publisher.provider.ProviderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FirstRunTest {

    private final ConfigService config = ConfigService.getInstance();
    private final WordPressSiteService sites = WordPressSiteService.getInstance();
    private WordPressSite addedSite;

    @BeforeEach
    void requireACleanMachine() {
        // المزودون يقرؤون المفتاح من متغيرات البيئة أيضًا؛ على جهاز فيه OPENAI_API_KEY
        // مثلًا لا توجد حالة "لم يُعَدّ شيء" لتُختبر
        assumeTrue(ProviderRegistry.contentGenerators().stream().noneMatch(ContentGenerator::isConfigured));
        assumeTrue(sites.getAllSites().isEmpty());
    }

    @AfterEach
    void cleanUp() throws IOException {
        config.setSetupCompleted(false);
        config.set(ConfigService.GEMINI_API_KEY, null);
        if (addedSite != null) {
            sites.deleteSite(addedSite.getId());
        }
    }

    @Test
    void nothingConfigured_needsSetup() {
        assertTrue(FirstRun.needsSetup());
    }

    @Test
    void completedFlag_hidesTheWizardEvenWithNothingConfigured() {
        config.setSetupCompleted(true);

        assertFalse(FirstRun.needsSetup());
    }

    @Test
    void existingAiKey_meansAnUpgradingUserNeverSeesTheWizard() {
        config.set(ConfigService.GEMINI_API_KEY, "some-key");

        assertFalse(FirstRun.needsSetup());
    }

    @Test
    void existingWordPressSite_meansAnUpgradingUserNeverSeesTheWizard() throws IOException {
        addedSite = sites.addSite("Site", "https://example.com", "user", "pass");

        assertFalse(FirstRun.needsSetup());
    }

    @Test
    void setupCompleted_roundTripsAndClearingRemovesTheKey() {
        config.setSetupCompleted(true);
        assertTrue(config.isSetupCompleted());

        config.setSetupCompleted(false);
        assertFalse(config.isSetupCompleted());
    }
}
