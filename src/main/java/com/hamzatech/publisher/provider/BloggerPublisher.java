package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.model.ArticleContent;
import com.hamzatech.publisher.model.PublishPlatform;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.service.BloggerService;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.List;

/**
 * النشر إلى مدونات Blogger عبر واجهة Google.
 */
@Log4j2
public class BloggerPublisher implements Publisher {

    @Override
    public PublishPlatform platform() {
        return PublishPlatform.BLOGGER;
    }

    @Override
    public PublishedPost publish(PublishTarget target, ArticleContent article, String category, boolean draft)
            throws IOException {
        List<String> labels = category != null ? List.of(category) : null;

        String postId = BloggerService.getInstance().publishPost(
                target.getId(), article.publishTitle(), article.contentHtml(),
                labels, draft, article.customSlug());

        // المسودة لا رابط عامًّا لها بعد؛ رابط المحرر هو ما يحتاجه من سيراجعها
        String url = draft ? editorUrl(target.getId(), postId) : resolveUrl(target, postId);
        return new PublishedPost(postId, url);
    }

    static String editorUrl(String blogId, String postId) {
        return "https://www.blogger.com/blog/post/edit/" + blogId + "/" + postId;
    }

    /**
     * Blogger لا يعيد رابط المقال مع إنشائه، فنجلبه بنداء إضافي. وإذا فشل ذلك
     * نبني رابطًا من عنوان المدونة بدل ترك السجل بلا رابط.
     */
    private static String resolveUrl(PublishTarget target, String postId) {
        try {
            String url = BloggerService.getInstance().getPostUrl(target.getId(), postId);
            if (url != null && !url.isBlank()) {
                return url;
            }
        } catch (Exception e) {
            log.debug("Could not fetch the post URL for {}, building one instead: {}", postId, e.getMessage());
        }

        String blogUrl = target.getUrl();
        if (blogUrl == null || blogUrl.isBlank()) {
            return null;
        }
        return String.format("https://%s/p/%s.html", blogUrl.replaceFirst("https?://", ""), postId);
    }
}
