package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.model.EffectiveSettings;

import java.io.IOException;

/**
 * شبكة اجتماعية يُشارَك فيها رابط المقال بعد نشره (قناة تيليجرام، صفحة فيسبوك).
 * <p>
 * ليست {@link Publisher}: المقال الكامل بصيغة HTML لا يناسب منشورًا اجتماعيًا،
 * فما يُرسَل هنا هو العنوان والوصف والرابط فقط، بعد أن يصير للمقال رابط عام.
 * <p>
 * <b>لإضافة شبكة جديدة</b>: حقّق هذه الواجهة في كلاس جديد، وأضف سطرًا في
 * {@code ProviderRegistry.registerBuiltIns()}، وحقلًا لوجهتها في
 * {@code PublishingProfile}.
 */
public interface SocialSharer {

    /** معرّف ثابت، مثل {@code "telegram"}. */
    String id();

    /** الاسم المعروض في الواجهة وفي سجل النشر. */
    String displayName();

    /** هل التوكن المطلوب موجود في الإعدادات؟ */
    boolean isConfigured();

    /**
     * أين تُشارَك مقالات هذه الوجهة على هذه الشبكة.
     *
     * @return معرّف القناة/الصفحة، أو {@code null} إن لم تُضبط الوجهة للمشاركة هنا
     */
    String destination(EffectiveSettings settings);

    /**
     * يشارك المقال.
     *
     * @param destination ما أعاده {@link #destination(EffectiveSettings)}، غير فارغ
     * @return رابط المنشور الاجتماعي إن أمكن معرفته، وإلا {@code null}
     * @throws IOException          إذا رفضت الشبكة الطلب
     * @throws IllegalStateException إذا لم يكن التوكن مضبوطًا
     */
    String share(SharedPost post, String destination) throws IOException;

    /**
     * ما يُشارَك: لا HTML المقال، بل ما يكفي لدعوة القارئ إليه.
     *
     * @param title       العنوان المنشور
     * @param description وصف الميتا، أو {@code null}
     * @param url         الرابط العام للمقال — لا يكون فارغًا
     * @param imageUrl    رابط صورة الرأس على الإنترنت، أو {@code null} (لا {@code data:} URI)
     */
    record SharedPost(String title, String description, String url, String imageUrl) {
    }
}
