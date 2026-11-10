package com.hamzatech.publisher.provider;

import java.io.IOException;
import java.util.Set;

/**
 * مزوّد يوفّر صورة رأس المقال، بحثًا أو توليدًا.
 *
 * @see ContentGenerator لسبب وجود هذه الواجهة
 */
public interface ImageProvider extends KeyedProvider {

    /** المعرّف المخزَّن في الإعدادات، مثل {@code "unsplash"}. */
    String id();

    /** الاسم المعروض في الواجهة. */
    String displayName();

    /** هل المفتاح المطلوب متوفر؟ */
    boolean isConfigured();

    /**
     * @return رابط الصورة، أو {@code data:} URI يحمل الصورة نفسها
     * @throws IllegalStateException إذا لم يكن المزود مهيّأً
     */
    String imageForArticle(String articleTitle) throws IOException;

    /**
     * صورة تتجنّب صورًا سبق نشرها على نفس الوجهة، حين يستطيع المزود الاختيار.
     * <p>
     * المزود الذي يولّد صورة جديدة في كل مرة لا يحتاج ذلك، فالسلوك الافتراضي
     * يتجاهل القائمة.
     *
     * @param avoid مفاتيح الصور المستخدمة من قبل، بصيغة {@code UsedImages.key}
     */
    default String imageForArticle(String articleTitle, Set<String> avoid) throws IOException {
        return imageForArticle(articleTitle);
    }

    /**
     * هل يعيد المزود رابطًا دائمًا على الإنترنت؟
     * <p>
     * المنصة التي لا ترفع الصور بنفسها (Blogger) لا تستطيع استخدام صورة يعيدها
     * المزود بياناتٍ لا رابطًا — انظر {@code ImageService.getImageForArticle(String, PublishTarget)}.
     */
    default boolean returnsHostedUrl() {
        return true;
    }
}
