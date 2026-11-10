package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.ArticleContent;
import com.hamzatech.publisher.model.EffectiveSettings;
import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.model.SeoMetadata;
import com.hamzatech.publisher.provider.ProviderRegistry;
import com.hamzatech.publisher.provider.Publisher;
import lombok.extern.log4j.Log4j2;

import java.util.function.Consumer;

/**
 * يولّد وينشر مقالاً واحداً كاملاً (ترجمة + توليد محتوى + صورة + نشر) ويسجّل النتيجة.
 * مستخرجة من DashboardController لإعادة استخدامها في النشر الدفعي، إعادة محاولة المقالات الفاشلة،
 * ومعاينة المقال قبل النشر.
 */
@Log4j2
public class ArticlePublishingService {

    public enum Stage {TRANSLATING, GENERATING_CONTENT, FETCHING_IMAGE, PUBLISHING}

    private ArticlePublishingService() {
    }

    /**
     * توليد محتوى المقال فقط (بدون نشر) - يُستخدم للمعاينة قبل النشر الفعلي.
     *
     * @param target الوجهة المستهدَفة، لتُطبَّق إعداداتها الخاصة إن وُجدت
     *               ({@link PublishingProfileService}) — أو {@code null} إن لم
     *               تُختَر بعد (كما في نافذة المعاينة قبل اختيار مدونة)
     */
    public static ArticleContent generateContent(String title, PublishTarget target, Consumer<Stage> onStage)
            throws java.io.IOException {
        return generateContent(title, target, onStage, new TranslationService());
    }

    /**
     * @param translationService يُمرَّر لأنه يحمل حالة تستحق إعادة الاستخدام عبر الدفعة
     *                           (عميل HTTP وموديل Gemini المُكتشَف). توليد المحتوى
     *                           والصورة لا يحتاجان تمريرًا: المزودون في
     *                           {@link ProviderRegistry} يحتفظون بعملائهم بأنفسهم.
     */
    public static ArticleContent generateContent(String title, PublishTarget target, Consumer<Stage> onStage,
                                                   TranslationService translationService) throws java.io.IOException {
        EffectiveSettings settings = PublishingProfileService.getInstance().resolve(target);
        AIService aiService = new AIService(settings.aiProvider());
        ImageService imageService = new ImageService(settings.imageProvider());

        String contentLanguage = settings.contentLanguage();
        String titleLanguage = settings.titleLanguage();
        String slugType = settings.slugType();

        String translatedTitle = null;
        boolean needsEnglishTitle = "english".equals(titleLanguage)
                || "english".equals(contentLanguage)
                || "english".equals(slugType);

        if (needsEnglishTitle) {
            if (onStage != null) onStage.accept(Stage.TRANSLATING);
            translatedTitle = translateTitleToEnglish(translationService, title);
            if (translatedTitle == null) {
                log.warn("Falling back to the original title for: {}", title);
            }
        }

        String publishTitle = "english".equals(titleLanguage) && translatedTitle != null
                ? translatedTitle
                : title;

        String contentTitle = "english".equals(contentLanguage) && translatedTitle != null
                ? translatedTitle
                : title;

        if (onStage != null) onStage.accept(Stage.GENERATING_CONTENT);
        log.debug("Generating content for: {}", contentTitle);
        ConfigService config = ConfigService.getInstance();
        String content = generateCheckedArticle(
                () -> aiService.generateArticle(contentTitle, contentLanguage),
                config.getArticleLength(), config.isQualityCheckEnabled());
        SeoMetadata seo = aiService.generateSeo(publishTitle, content, contentLanguage);
        if (config.isInternalLinksEnabled()) {
            content = InternalLinks.addTo(content, title, contentTitle, target, contentLanguage);
        }
        log.debug("SEO for '{}': {}", publishTitle, SeoService.summary(seo));

        if (onStage != null) onStage.accept(Stage.FETCHING_IMAGE);
        String imageTitle = translatedTitle != null ? translatedTitle : title;
        log.debug("Fetching image for: {}", imageTitle);
        String imageUrl = imageService.getImageForArticle(imageTitle, target);

        // الصورة لا تُدمج هنا: ArticleContent يركّبها عند الحاجة، وووردبريس يأخذها
        // صورة بارزة مستقلة فلا تظهر مرتين
        String bodyHtml = "<div>" + content + "</div>";

        String customSlug = null;
        if ("custom".equals(slugType)) {
            customSlug = BloggerService.generateSlugFromTitle(title);
        } else if ("english".equals(slugType)) {
            customSlug = translatedTitle != null
                    ? BloggerService.generateEnglishSlug(translatedTitle)
                    : BloggerService.generateSlugFromTitle(title);
        }

        return new ArticleContent(title, publishTitle, bodyHtml, imageUrl, customSlug, seo);
    }

    /** مصدر نص المقال؛ واجهة لا {@code Supplier} لأن التوليد يرمي {@link java.io.IOException}. */
    @FunctionalInterface
    interface ArticleSource {
        String generate() throws java.io.IOException;
    }

    /**
     * يولّد المقال ويفحص جودته، ويعيد التوليد مرة واحدة إن رُفض.
     * <p>
     * الرد الفاشل غالبًا عارض — نفس الطلب ينجح في المرة التالية — فإعادة واحدة
     * توفّر على المستخدم إعادة المحاولة يدويًا من السجل. وهي آمنة بخلاف إعادة
     * النشر: لم يُنشأ أي مقال بعد. وإن رُفض الرد الثاني يفشل المقال بالسبب، فيظهر
     * في السجل وتُعاد محاولته لاحقًا كأي فشل.
     */
    static String generateCheckedArticle(ArticleSource source, String length, boolean check)
            throws java.io.IOException {
        String content = source.generate();
        if (!check) {
            return content;
        }
        String problem = ArticleQualityCheck.problem(content, length);
        if (problem == null) {
            return content;
        }
        log.warn("Generated article rejected ({}); regenerating once.", problem);
        content = source.generate();
        ArticleQualityCheck.require(content, length);
        return content;
    }

    /**
     * نشر مقال منفرد (مناسب لإعادة محاولة مقال واحد).
     */
    public static PublishLog publishArticle(String title, PublishTarget target, String category, Consumer<Stage> onStage) {
        return publishArticle(title, target, category, onStage, new TranslationService());
    }

    /**
     * نشر مقال مع إعادة استخدام خدمة الترجمة عبر الدفعة.
     * يوجّه النشر إلى المنصة المناسبة عبر {@link ProviderRegistry}.
     */
    public static PublishLog publishArticle(String title, PublishTarget target, String category, Consumer<Stage> onStage,
                                             TranslationService translationService) {
        try {
            ArticleContent articleContent = generateContent(title, target, onStage, translationService);
            EffectiveSettings settings = PublishingProfileService.getInstance().resolve(target);
            boolean draft = settings.draft();

            if (onStage != null) onStage.accept(Stage.PUBLISHING);
            log.debug("Publishing article{}: {}", draft ? " as draft" : "", articleContent.publishTitle());

            Publisher.PublishedPost post = ProviderRegistry.publisher(target.getPlatform())
                    .publish(target, articleContent, category, draft);

            log.info("✅ Article {} successfully: {} (ID: {})", draft ? "saved as draft" : "published",
                    title, post.postId());

            PublishLog successLog = PublishLog.success(title, target.getName(), target.getId(),
                    post.postId(), post.postUrl(), category);
            successLog.setDraft(draft);
            successLog.setImageUrl(UsedImages.loggable(articleContent.imageUrl()));
            PublishLogService.getInstance().addLog(successLog);
            shareAfterPublish(successLog, articleContent, settings);
            return successLog;

        } catch (Exception e) {
            log.error("❌ Failed to publish article: {}", title, e);
            PublishLog failureLog = PublishLog.failure(title, target.getName(), target.getId(), category, e.getMessage());
            PublishLogService.getInstance().addLog(failureLog);
            return failureLog;
        }
    }

    /**
     * يشارك المقال المنشور في الشبكات الاجتماعية المضبوطة لوجهته، ويسجّل النتيجة.
     * <p>
     * يُستدعى بعد كتابة سجل النجاح، ولا يرمي أبدًا: لو وصل استثناء من هنا إلى
     * {@code catch} في {@link #publishArticle} لسُجِّل مقال منشور فعلًا كفاشل، وإعادة
     * محاولته تنشره مرة ثانية.
     */
    static void shareAfterPublish(PublishLog successLog, ArticleContent article, EffectiveSettings settings) {
        try {
            SocialSharing.Outcome outcome = SocialSharing.shareAll(
                    article, successLog.getPostUrl(), successLog.isDraft(), settings);
            if (!outcome.attempted()) {
                return;
            }
            successLog.setSharedTo(outcome.sharedTo().isEmpty() ? null : outcome.sharedTo());
            successLog.setShareError(outcome.error());
            PublishLogService.getInstance().updateSharing(successLog);
        } catch (RuntimeException e) {
            log.warn("Social sharing failed for '{}': {}", successLog.getTitle(), e.getMessage());
        }
    }

    /**
     * ترجمة العنوان إلى الإنجليزية
     *
     * @return الترجمة، أو null في حالة الفشل أو إذا كانت النتيجة فارغة
     */
    private static String translateTitleToEnglish(TranslationService translationService, String title) {
        try {
            String translated = translationService.translateToEnglish(title);

            if (translated == null || translated.isBlank()) {
                log.warn("Title translation returned an empty result for: {}", title);
                return null;
            }

            translated = stripSurroundingQuotes(translated.trim());

            if (translated.isBlank()) {
                log.warn("Title translation contained only quotes for: {}", title);
                return null;
            }

            return translated;
        } catch (Exception ex) {
            log.warn("Title translation failed for '{}': {}", title, ex.getMessage());
            return null;
        }
    }

    /**
     * إزالة علامات الاقتباس المحيطة بالنص (مستقيمة أو ذكية)
     */
    private static String stripSurroundingQuotes(String text) {
        String result = text;

        while (result.length() >= 2) {
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);

            boolean doubleQuoted = (first == '"' || first == '“') && (last == '"' || last == '”');
            boolean singleQuoted = (first == '\'' || first == '‘') && (last == '\'' || last == '’');

            if (!doubleQuoted && !singleQuoted) {
                break;
            }

            result = result.substring(1, result.length() - 1).trim();
        }

        return result;
    }
}
