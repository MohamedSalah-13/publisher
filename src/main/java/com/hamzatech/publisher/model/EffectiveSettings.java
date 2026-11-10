package com.hamzatech.publisher.model;

/**
 * إعدادات النشر بعد تسوية ملف تعريف الوجهة مع الإعدادات العامة.
 * <p>
 * كل حقل هنا محسوم القيمة — لا {@code null} فيه إلا {@link #defaultCategory()}
 * ووجهتَي المشاركة {@link #telegramChat()} و{@link #facebookPageId()}، التي لا
 * تملك مقابلًا عامًّا أصلًا ({@code null} فيها يعني: لا مشاركة). هذا ما يجعل استهلاكه في خط أنابيب النشر
 * مباشرًا: لا حاجة لأي طرف مستهلِك أن يعرف مصدر كل قيمة أو يكرّر منطق التسوية.
 *
 * @see com.hamzatech.publisher.service.PublishingProfileService#resolve(PublishTarget)
 */
public record EffectiveSettings(String aiProvider,
                                 String imageProvider,
                                 String contentLanguage,
                                 String titleLanguage,
                                 String slugType,
                                 int publishDelaySeconds,
                                 String defaultCategory,
                                 String postStatus,
                                 String telegramChat,
                                 String facebookPageId) {

    /** هل يُحفظ المقال مسودة للمراجعة بدل نشره مباشرة؟ */
    public boolean draft() {
        return "draft".equals(postStatus);
    }
}
