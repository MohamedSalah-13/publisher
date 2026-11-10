package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.SeoMetadata;
import com.hamzatech.publisher.provider.ContentGenerator;
import com.hamzatech.publisher.provider.ProviderRegistry;

import java.io.IOException;

/**
 * واجهة موحدة لخدمات الذكاء الاصطناعي.
 * <p>
 * كانت تحمل سلسلة {@code if} تختار بين OpenAI و Gemini وتدير عميل كل منهما.
 * صار ذلك مسؤولية {@link ContentGenerator} و{@link ProviderRegistry}، وبقيت هذه
 * الواجهة كما هي حتى لا يتغيّر أي مستدعٍ.
 */
public class AIService {

    private final ConfigService configService;
    private final String providerOverride;

    public AIService() {
        this(null);
    }

    /**
     * @param providerOverride معرّف مزوّد صريح (من ملف تعريف الوجهة مثلًا)، أو
     *                         {@code null} لاستخدام الإعداد العام
     */
    public AIService(String providerOverride) {
        this.configService = ConfigService.getInstance();
        this.providerOverride = providerOverride;
    }

    public String generateArticle(String title) throws IOException {
        return generateArticle(title, "arabic");
    }

    public String generateArticle(String title, String targetLanguage) throws IOException {
        return generator().generateArticle(title, targetLanguage);
    }

    /**
     * بيانات SEO للمقال بالمزود نفسه، أو بديل مستخرج من المقال إن عُطّلت في
     * الإعدادات. لا يرمي استثناءً أبدًا — انظر {@link SeoService}.
     */
    public SeoMetadata generateSeo(String title, String bodyHtml, String language) {
        return SeoService.generate(title, bodyHtml, language,
                configService.isSeoAiEnabled() ? generator() : null);
    }

    /**
     * عناوين مقترحة لموضوع، بالمزود نفسه. يرمي عند الفشل — انظر {@link TitleIdeasService}.
     */
    public java.util.List<String> generateTitles(String topic, int count, String language) throws IOException {
        return TitleIdeasService.generate(topic, count, language, generator());
    }

    /**
     * هل المزود المختار جاهز للاستخدام (مفتاحه متوفر)؟
     */
    public boolean isConfigured() {
        return generator().isConfigured();
    }

    public String getCurrentProvider() {
        return providerOverride != null ? providerOverride : configService.getAIProvider();
    }

    private ContentGenerator generator() {
        return ProviderRegistry.contentGenerator(getCurrentProvider());
    }
}
