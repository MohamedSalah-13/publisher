package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.model.PublishTarget;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * الصور التي سبق نشرها على وجهة، حتى لا تحمل مقالات متشابهة العنوان نفس الصورة.
 * <p>
 * المصدر الدائم هو {@code publish_logs.json}: كل مقال ناجح يسجّل رابط صورته،
 * فيرى المقال التالي في نفس الدفعة صورة سابقه. السجلات القديمة بلا رابط صورة
 * لا تمنع شيئًا. تُضاف إليه الصور المحجوزة في هذا التشغيل، لمقالات لم تُنشر بعد.
 */
public final class UsedImages {

    /**
     * صور اختيرت في هذا التشغيل، لكل وجهة.
     * <p>
     * السجل وحده لا يكفي: الصورة لا تُسجَّل إلا بعد نشر المقال، فمقالان لنفس
     * الوجهة يعملان معًا (إعادة محاولة صفّين فاشلين، كلٌّ في خيطه) يقرآن السجل
     * نفسه ويختاران الصورة نفسها. المجموعة هي أيضًا قفل الاختيار للوجهة.
     */
    private static final Map<String, Set<String>> RESERVED = new ConcurrentHashMap<>();

    private UsedImages() {
    }

    /** طريقة اختيار الصورة بمعرفة ما يجب تجنّبه؛ واجهة لا {@code Function} لأنها ترمي {@link IOException}. */
    @FunctionalInterface
    public interface Selection {
        String pick(Set<String> avoid) throws IOException;
    }

    /**
     * يختار صورة للوجهة ويحجزها في خطوة واحدة.
     * <p>
     * الاختيار لنفس الوجهة يجري واحدًا تلو الآخر، فلا يرى اختياران متزامنان
     * نفس قائمة الصور المستخدمة. وجهات مختلفة لا تنتظر بعضها.
     *
     * @param target الوجهة، أو {@code null} (المعاينة) فلا تجنّب ولا حجز
     */
    public static String select(PublishTarget target, Selection selection) throws IOException {
        if (target == null || target.getId() == null) {
            return selection.pick(Set.of());
        }
        Set<String> reserved = RESERVED.computeIfAbsent(target.getId(), id -> ConcurrentHashMap.newKeySet());
        synchronized (reserved) {
            Set<String> avoid = new HashSet<>(forTarget(target));
            avoid.addAll(reserved);
            String image = selection.pick(avoid);
            String loggable = loggable(image);
            if (loggable != null) {
                reserved.add(key(loggable));
            }
            return image;
        }
    }

    /** للاختبارات: الحجوزات تعيش طوال تشغيل JVM. */
    static void clearReservations() {
        RESERVED.clear();
    }

    /**
     * @return مفاتيح صور المقالات الناجحة على الوجهة، أو مجموعة فارغة لوجهة {@code null} (المعاينة)
     */
    public static Set<String> forTarget(PublishTarget target) {
        if (target == null || target.getId() == null) {
            return Set.of();
        }
        return fromLogs(PublishLogService.getInstance().getLogsByBlog(target.getId()));
    }

    static Set<String> fromLogs(List<PublishLog> logs) {
        Set<String> keys = new HashSet<>();
        for (PublishLog log : logs) {
            if ("SUCCESS".equals(log.getStatus()) && log.getImageUrl() != null) {
                keys.add(key(log.getImageUrl()));
            }
        }
        return keys;
    }

    /**
     * يعرّف الصورة بغض النظر عن معاملات الرابط.
     * <p>
     * Unsplash يعيد نفس الصورة بمعامل {@code ixid} مختلف في كل طلب، فمقارنة
     * الرابط كاملًا لا تكشف التكرار أبدًا.
     */
    public static String key(String url) {
        int query = url.indexOf('?');
        return query >= 0 ? url.substring(0, query) : url;
    }

    /**
     * @return الرابط إن كان يستحق التسجيل، أو {@code null} لصورة {@code data:} —
     * صورة مولَّدة لا تتكرر، ونصّها الطويل لا مكان له في ملف السجل
     */
    public static String loggable(String imageUrl) {
        return imageUrl != null && imageUrl.startsWith("http") ? imageUrl : null;
    }
}
