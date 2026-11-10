package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.model.PublishTarget;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * يمنع نشر المقال نفسه إلى الوجهة نفسها مرتين.
 * <p>
 * الحالة التي يعالجها واقعية: يُعاد تشغيل ملف Excel بعد فشل جزئي، أو يُضاف إليه
 * عنوان جديد ويُعاد رفعه كاملًا، فتُنشر المقالات القديمة من جديد. البصمة هي
 * العنوان + معرّف الوجهة: العنوان نفسه على مدونتين مختلفتين نشرٌ مقصود، وعلى
 * المدونة نفسها تكرار.
 */
public interface DuplicateGuard {

    /**
     * فاصل جزأي البصمة.
     * <p>
     * سطر جديد وليس مسافة: التوحيد يطوي كل المسافات المتتالية إلى مسافة واحدة،
     * فلو كان الفاصل مسافة لأنتج {@code ("a", "b c")} و{@code ("a b", "c")} البصمة
     * نفسها. أما السطر الجديد فلا يبقى في عنوان مُوحَّد ولا يظهر في معرّف وجهة.
     */
    String SEPARATOR = "\n";

    /** لا يمنع شيئًا. */
    DuplicateGuard ALLOW_ALL = new DuplicateGuard() {
        @Override
        public boolean alreadyPublished(String title, PublishTarget target) {
            return false;
        }

        @Override
        public void recordPublished(String title, PublishTarget target) {
            // لا شيء
        }
    };

    boolean alreadyPublished(String title, PublishTarget target);

    /**
     * يسجّل ما نُشر للتو، حتى يُكتشف التكرار داخل الدفعة الواحدة أيضًا —
     * ملف Excel قد يحمل العنوان نفسه مرتين.
     */
    void recordPublished(String title, PublishTarget target);

    /**
     * حارس يقرأ سجل النشر مرة واحدة عند إنشائه.
     * <p>
     * {@link PublishLogService#getAllLogs()} يقرأ الملف من القرص في كل نداء، فلو
     * سُئل مرة لكل مقال لأعاد تحليل JSON عشرات المرات في الدفعة الواحدة.
     */
    static DuplicateGuard fromPublishLog() {
        Set<String> published = new HashSet<>();
        for (PublishLog entry : PublishLogService.getInstance().getAllLogs()) {
            if (entry.isSuccess()) {
                published.add(fingerprint(entry.getTitle(), entry.getBlogId()));
            }
        }

        return new DuplicateGuard() {
            @Override
            public boolean alreadyPublished(String title, PublishTarget target) {
                return published.contains(fingerprint(title, target.getId()));
            }

            @Override
            public void recordPublished(String title, PublishTarget target) {
                published.add(fingerprint(title, target.getId()));
            }
        };
    }

    /**
     * يوحّد العنوان قبل المقارنة: المسافات الزائدة في خلايا Excel شائعة، وفرق
     * حالة الأحرف في عنوان إنجليزي ليس مقالًا آخر.
     */
    static String fingerprint(String title, String targetId) {
        String normalized = title == null ? ""
                : title.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return (targetId == null ? "" : targetId) + SEPARATOR + normalized;
    }
}
