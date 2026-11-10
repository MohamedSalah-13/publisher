package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.model.ArticleContent;
import com.hamzatech.publisher.model.PublishPlatform;
import com.hamzatech.publisher.model.PublishTarget;

import java.io.IOException;

/**
 * منصة يُنشر إليها المقال.
 * <p>
 * كان التوجيه بين Blogger و WordPress فرعَي {@code if} داخل
 * {@code ArticlePublishingService}، فإضافة منصة ثالثة تعني تعديله. الآن المنصة
 * كلاس يحقق هذه الواجهة ويُسجَّل في {@link ProviderRegistry}.
 */
public interface Publisher {

    /** المنصة التي يتولاها هذا المزود. */
    PublishPlatform platform();

    /**
     * ينشر المقال.
     *
     * @param category التصنيف/الوسم، أو {@code null} إن لم يُختَر
     * @param draft    {@code true} لحفظ المقال مسودة للمراجعة بدل نشره للعامة
     * @return معرّف المقال ورابطه — رابط تحريره إن كان مسودة، إذ لا رابط عام لها
     */
    PublishedPost publish(PublishTarget target, ArticleContent article, String category, boolean draft)
            throws IOException;

    /**
     * نتيجة النشر: المعرّف والرابط.
     * <p>
     * المنصتان تعطيان هذين بطريقتين مختلفتين — WordPress يعيد الرابط مباشرة في
     * استجابة الإنشاء، بينما Blogger يحتاج نداءً إضافيًا لجلبه — وهذا السجل يخفي
     * ذلك الاختلاف عن المستدعي.
     */
    record PublishedPost(String postId, String postUrl) {
    }
}
