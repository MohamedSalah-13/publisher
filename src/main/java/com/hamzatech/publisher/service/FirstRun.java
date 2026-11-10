package com.hamzatech.publisher.service;

import com.hamzatech.publisher.provider.ContentGenerator;
import com.hamzatech.publisher.provider.ProviderRegistry;

import java.io.IOException;

/**
 * هل يُعرض معالج الإعداد عند التشغيل؟
 * <p>
 * المعالج لمن لم يُعِدّ شيئًا بعد. من يحدّث من إصدار أقدم وعنده مفتاح أو موقع
 * محفوظ لا يراه أبدًا، حتى لو لم تُكتب علامة الإكمال عنده.
 */
public final class FirstRun {

    private FirstRun() {
    }

    public static boolean needsSetup() {
        if (ConfigService.getInstance().isSetupCompleted()) {
            return false;
        }
        boolean hasGenerator = ProviderRegistry.contentGenerators().stream()
                .anyMatch(ContentGenerator::isConfigured);
        boolean hasSite = !WordPressSiteService.getInstance().getAllSites().isEmpty();
        return !hasGenerator && !hasSite;
    }

    /** يُستدعى عند إنهاء المعالج أو تخطّيه، فلا يظهر من تلقاء نفسه مرة أخرى. */
    public static void markCompleted() throws IOException {
        ConfigService config = ConfigService.getInstance();
        config.setSetupCompleted(true);
        config.saveConfig();
    }
}
