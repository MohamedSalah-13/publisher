package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.model.ArticleContent;
import com.hamzatech.publisher.model.PublishPlatform;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.service.WordPressService;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * النشر إلى مواقع WordPress عبر REST API.
 */
@Log4j2
public class WordPressPublisher implements Publisher {

    private final WordPressService wordPressService = new WordPressService();

    /**
     * الاسم مقابل المعرّف لكل موقع.
     * <p>
     * ووردبريس يربط المقال بمعرّف رقمي لا باسم، وحلّ الاسم يحتاج نداء شبكة.
     * بلا تخبئة لتكرّر ذلك النداء مع كل مقال في الدفعة رغم أن التصنيف واحد.
     */
    private final Map<String, Integer> categoryIds = new ConcurrentHashMap<>();

    /** مثل {@link #categoryIds} للوسوم. */
    private final Map<String, Integer> tagIds = new ConcurrentHashMap<>();

    @Override
    public PublishPlatform platform() {
        return PublishPlatform.WORDPRESS;
    }

    @Override
    public PublishedPost publish(PublishTarget target, ArticleContent article, String category, boolean draft)
            throws IOException {
        Integer featuredMediaId = featuredMediaId(target, article);

        // إذا صارت الصورة بارزة فلا تُدرَج في الجسم أيضًا، وإلا ظهرت مرتين في
        // القوالب التي تعرض الصورة البارزة. وصورة مولَّدة فشل رفعها لا تُضمَّن في
        // الجسم أيضًا: بياناتها مئات الكيلوبايت داخل كود المقال
        boolean embeddedImage = article.imageUrl() != null && article.imageUrl().startsWith("data:");
        String content = featuredMediaId != null || embeddedImage ? article.bodyHtml() : article.contentHtml();

        WordPressService.PostOptions options = new WordPressService.PostOptions(
                categoryId(target, category), featuredMediaId, draft,
                article.seo().description(), tagIds(target, article.seo().tags()));

        String postUrl = wordPressService.createPost(
                target.getUrl(), target.getWpUsername(), target.getWpApplicationPassword(),
                article.publishTitle(), content, article.customSlug(), options);

        // استجابة الإنشاء تحمل الرابط مباشرة، ولا معرّف منفصل نحتاجه في السجل
        return new PublishedPost(postUrl, postUrl);
    }

    /**
     * يرفع صورة الرأس إلى مكتبة الوسائط ويعيد معرّفها.
     * <p>
     * الفشل هنا لا يُفشل النشر: نعود إلى وضع الصورة داخل الجسم كما كان الحال قبل
     * دعم الصورة البارزة، فالمقال يُنشر بصورته على كل حال.
     */
    private Integer featuredMediaId(PublishTarget target, ArticleContent article) {
        if (article.imageUrl() == null || article.imageUrl().isBlank()) {
            return null;
        }
        int mediaId;
        try {
            mediaId = wordPressService.uploadMedia(
                    target.getUrl(), target.getWpUsername(), target.getWpApplicationPassword(),
                    article.imageUrl(), article.publishTitle());
        } catch (IOException e) {
            log.warn("Could not upload the header image to {}; keeping it inside the article body: {}",
                    target.getName(), e.getMessage());
            return null;
        }

        // النص البديل تحسين لا شرط: فشله لا يُسقط الصورة البارزة
        try {
            wordPressService.setMediaAltText(target.getUrl(), target.getWpUsername(),
                    target.getWpApplicationPassword(), mediaId, article.imageAlt());
        } catch (IOException e) {
            log.warn("Could not set the image alt text on {}: {}", target.getName(), e.getMessage());
        }
        return mediaId;
    }

    /**
     * يحوّل أسماء الوسوم إلى معرّفات، مُنشئًا ما لم يوجد.
     * <p>
     * وسم تعذّر حلّه يُتجاوَز وحده: الوسوم تحسين للبحث، وفقدان أحدها لا يستحق
     * إفشال المقال.
     */
    private List<Integer> tagIds(PublishTarget target, List<String> tags) {
        List<Integer> ids = new ArrayList<>();
        for (String tag : tags) {
            String key = target.getId() + " " + tag.trim().toLowerCase(Locale.ROOT);
            Integer cached = tagIds.get(key);
            if (cached != null) {
                ids.add(cached);
                continue;
            }
            try {
                int id = wordPressService.findOrCreateTagId(
                        target.getUrl(), target.getWpUsername(), target.getWpApplicationPassword(), tag);
                tagIds.put(key, id);
                ids.add(id);
            } catch (IOException e) {
                log.warn("Could not resolve the WordPress tag '{}' on {}; skipping it: {}",
                        tag, target.getName(), e.getMessage());
            }
        }
        return ids;
    }

    /**
     * يحوّل اسم التصنيف إلى معرّف، ويُنشئه إن لم يوجد.
     * <p>
     * فشل هذه الخطوة لا يُفشل النشر: مقال منشور بلا تصنيف أفضل من مقال لم يُنشر،
     * خاصة أن سبب الفشل الغالب هو صلاحيات لا تسمح بإنشاء تصنيف.
     */
    private Integer categoryId(PublishTarget target, String category) {
        if (category == null || category.isBlank()) {
            return null;
        }

        String key = target.getId() + " " + category.trim().toLowerCase(Locale.ROOT);
        Integer cached = categoryIds.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            int id = wordPressService.findOrCreateCategoryId(
                    target.getUrl(), target.getWpUsername(), target.getWpApplicationPassword(), category);
            categoryIds.put(key, id);
            return id;
        } catch (IOException e) {
            log.warn("Could not resolve the WordPress category '{}' on {}; publishing without it: {}",
                    category, target.getName(), e.getMessage());
            return null;
        }
    }
}
