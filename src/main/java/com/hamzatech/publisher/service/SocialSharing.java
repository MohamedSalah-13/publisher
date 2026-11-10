package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.ArticleContent;
import com.hamzatech.publisher.model.EffectiveSettings;
import com.hamzatech.publisher.provider.ProviderRegistry;
import com.hamzatech.publisher.provider.SocialSharer;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * يشارك المقال المنشور في الشبكات المضبوطة لوجهته (تيليجرام، فيسبوك).
 * <p>
 * <b>لا يرمي أبدًا.</b> المقال منشور فعلًا حين يصل الأمر إلى هنا، ففشل المشاركة
 * لا يجوز أن يجعله فاشلًا — إعادة محاولة مقال "فاشل" تعني توليده ونشره من جديد،
 * أي منشورًا مكررًا على المدونة. الفشل يُسجَّل تحذيرًا وفي {@link Outcome#error()}.
 */
@Log4j2
public final class SocialSharing {

    /**
     * نتيجة المشاركة.
     *
     * @param sharedTo أسماء الشبكات التي نجحت المشاركة فيها، وقد تكون فارغة
     * @param error    أسباب الفشل مجمَّعة، أو {@code null}
     */
    public record Outcome(List<String> sharedTo, String error) {

        public Outcome {
            sharedTo = List.copyOf(sharedTo);
        }

        static Outcome none() {
            return new Outcome(List.of(), null);
        }

        /** هل حاولنا المشاركة في أي شبكة أصلًا؟ */
        public boolean attempted() {
            return !sharedTo.isEmpty() || error != null;
        }
    }

    private SocialSharing() {
    }

    /**
     * @param postUrl رابط المقال كما أعادته المنصة
     * @param draft   المسودة لا رابط عامًّا لها (الرابط رابط تحريرها)، فلا تُشارَك
     */
    public static Outcome shareAll(ArticleContent article, String postUrl, boolean draft, EffectiveSettings settings) {
        if (draft || !isPublicUrl(postUrl) || settings == null) {
            return Outcome.none();
        }

        SocialSharer.SharedPost post = postFor(article, postUrl);
        List<String> shared = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (SocialSharer sharer : ProviderRegistry.socialSharers()) {
            String destination;
            try {
                destination = sharer.destination(settings);
            } catch (RuntimeException e) {
                log.warn("Could not resolve the {} destination: {}", sharer.displayName(), e.getMessage());
                errors.add(sharer.displayName() + ": " + e.getMessage());
                continue;
            }
            if (destination == null) {
                continue;
            }
            if (!sharer.isConfigured()) {
                log.warn("Sharing to {} skipped: no token in the settings.", sharer.displayName());
                errors.add(sharer.displayName() + ": token missing in the settings");
                continue;
            }
            try {
                String link = sharer.share(post, destination);
                shared.add(sharer.displayName());
                log.info("Shared '{}' to {} ({})", post.title(), sharer.displayName(), link != null ? link : destination);
            } catch (Exception e) {
                // Exception لا IOException: خطأ برمجي في مزود واحد لا يوقف البقية ولا يفشل المقال
                log.warn("Sharing '{}' to {} failed: {}", post.title(), sharer.displayName(), e.getMessage());
                errors.add(sharer.displayName() + ": " + e.getMessage());
            }
        }

        return new Outcome(shared, errors.isEmpty() ? null : String.join("; ", errors));
    }

    /**
     * ما يُرسَل إلى الشبكات: العنوان المنشور، والوصف من SEO، وصورة الرأس فقط إن
     * كانت رابطًا على الإنترنت — صورة {@code data:} لا تستطيع شبكة جلبها، ومعاينة
     * الرابط تعرض الصورة البارزة للمقال على أي حال.
     */
    static SocialSharer.SharedPost postFor(ArticleContent article, String postUrl) {
        String image = article.imageUrl();
        boolean hostedImage = image != null && (image.startsWith("https://") || image.startsWith("http://"));
        String description = article.seo().hasDescription() ? article.seo().description() : null;
        return new SocialSharer.SharedPost(article.publishTitle(), description, postUrl, hostedImage ? image : null);
    }

    static boolean isPublicUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }
}
