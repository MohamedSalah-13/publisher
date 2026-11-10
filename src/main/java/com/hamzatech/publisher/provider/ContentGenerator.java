package com.hamzatech.publisher.provider;

import java.io.IOException;

/**
 * مزوّد يولّد نص المقال.
 * <p>
 * كان الاختيار بين OpenAI و Gemini سلسلة نصية و{@code if} داخل {@code AIService}،
 * فإضافة مزود ثالث تعني تعديل كلاس قائم. الآن المزود كلاس يحقق هذه الواجهة
 * ويُسجَّل في {@link ProviderRegistry}.
 */
public interface ContentGenerator extends KeyedProvider {

    /** المعرّف المخزَّن في الإعدادات، مثل {@code "openai"}. */
    String id();

    /** الاسم المعروض في الواجهة. */
    String displayName();

    /** هل المفتاح المطلوب متوفر؟ يُستخدم للتحقق قبل بدء دفعة نشر. */
    boolean isConfigured();

    /**
     * يولّد نص المقال.
     *
     * @param targetLanguage {@code "arabic"} أو {@code "english"}
     * @throws IllegalStateException إذا لم يكن المزود مهيّأً
     */
    String generateArticle(String title, String targetLanguage) throws IOException;

    /**
     * طلب نصي قصير عام (بيانات SEO مثلًا) بالنموذج نفسه المستخدم للمقال.
     * <p>
     * افتراضيًا غير مدعوم: المستدعي يعامل ذلك كفشل ويعود إلى بديل لا يحتاج نموذجًا،
     * فمزود جديد لا يُلزَم بتحقيقه ليعمل.
     *
     * @throws UnsupportedOperationException إذا لم يدعمه المزود
     */
    default String complete(String prompt) throws IOException {
        throw new UnsupportedOperationException(id() + " does not support free-form completion");
    }
}
