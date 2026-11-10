package com.hamzatech.publisher.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * إعدادات نشر خاصة بوجهة واحدة، تُغلّب على الإعدادات العامة عند وجودها.
 * <p>
 * كل حقل هنا {@code null} يعني "استخدم الإعداد العام" — لا قيمة افتراضية مكرّرة
 * هنا، فالإعداد العام ({@code ConfigService}) هو المصدر الوحيد للافتراضي. هذا ما
 * يجعل الترحيل تلقائيًا: موقع بلا ملف تعريف يسلك تمامًا كما كان قبل هذه الميزة.
 */
@Getter
@Setter
public class PublishingProfile implements Serializable {

    private String targetId;

    /** معرّف مزوّد المحتوى المسجَّل في {@code ProviderRegistry}، أو {@code null}. */
    private String aiProvider;

    /** معرّف مزوّد الصور المسجَّل في {@code ProviderRegistry}، أو {@code null}. */
    private String imageProvider;

    /** {@code "arabic"} أو {@code "english"}، أو {@code null}. */
    private String contentLanguage;

    /** {@code "arabic"} أو {@code "english"}، أو {@code null}. */
    private String titleLanguage;

    /** {@code "auto"} أو {@code "custom"} أو {@code "english"}، أو {@code null}. */
    private String slugType;

    /** التأخير بين المقالات بالثواني، أو {@code null} لاستخدام الإعداد العام. */
    private Integer publishDelaySeconds;

    /** التصنيف المُختار تلقائيًا عند اختيار هذه الوجهة، أو {@code null}. */
    private String defaultCategory;

    /** {@code "publish"} أو {@code "draft"}، أو {@code null}. */
    private String postStatus;

    /**
     * قناة تيليجرام التي تُشارَك فيها مقالات هذه الوجهة ({@code @channel} أو
     * {@code -100...})، أو {@code null} لعدم المشاركة. لا مقابل عامًّا لها:
     * المشاركة تُختار لكل وجهة على حدة.
     */
    private String telegramChat;

    /** معرّف صفحة فيسبوك التي تُشارَك فيها مقالات هذه الوجهة، أو {@code null}. */
    private String facebookPageId;

    public PublishingProfile() {
    }

    public PublishingProfile(String targetId) {
        this.targetId = targetId;
    }

    /** هل كل الحقول فارغة؟ ملف تعريف كهذا لا يستحق التخزين. */
    public boolean isEmpty() {
        return aiProvider == null && imageProvider == null && contentLanguage == null
                && titleLanguage == null && slugType == null && publishDelaySeconds == null
                && defaultCategory == null && postStatus == null
                && telegramChat == null && facebookPageId == null;
    }
}
