package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.service.KeyCheck;

/**
 * ما يحتاجه معالج الإعداد من أي مزود يعمل بمفتاح API: أين يُحفظ المفتاح، من أين
 * يحصل عليه المستخدم، وكيف يُفحص قبل حفظه.
 * <p>
 * كلها افتراضية، فمزود جديد يعمل دون تحقيقها؛ لكنه لا يظهر في معالج الإعداد إلا
 * إذا أعاد {@link #apiKeySetting()} قيمة. بذلك لا يحمل المعالج قائمة مزودين خاصة
 * به تُنسى عند إضافة مزود.
 */
public interface KeyedProvider {

    String id();

    String displayName();

    /** اسم الإعداد الذي يُحفظ فيه المفتاح في {@code ConfigService}، أو {@code null}. */
    default String apiKeySetting() {
        return null;
    }

    /** صفحة إنشاء المفتاح عند المزود، أو {@code null}. */
    default String apiKeyPage() {
        return null;
    }

    /** يفحص مفتاحًا مرشّحًا لم يُحفظ بعد. لا يرمي: النتيجة تصف ما حدث. */
    default KeyCheck.Result checkKey(String apiKey) {
        return KeyCheck.Result.unsupported();
    }
}
