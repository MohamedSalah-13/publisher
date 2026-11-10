package com.hamzatech.publisher.service;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * يفحص المقال المولَّد قبل نشره.
 * <p>
 * النموذج قد يعيد اعتذارًا ("عذرًا، لا أستطيع...")، أو نصًّا مقطوعًا عند حد
 * الرموز، أو Markdown بدل HTML، أو سطرين بدل مقال. بلا فحص كان كل ذلك يُنشر
 * حرفيًا — وفي دفعة من مئة عنوان لا ينتبه أحد حتى يراه القرّاء.
 * <p>
 * القواعد مقصودة أن تكون متساهلة: تلتقط الردود الفاشلة بوضوح، لا المقالات
 * المتوسطة. مقال جيد يُرفض خطأً أسوأ من مقال ضعيف يمر.
 */
public final class ArticleQualityCheck {

    /** نسبة الطول المطلوب التي دونها يُعد المقال ناقصًا. */
    static final double MIN_LENGTH_RATIO = 0.5;

    /** الاعتذار يأتي في أول الرد؛ فحص ما بعده يلتقط مقالات تناقش الموضوع نفسه. */
    private static final int OPENING_CHARS = 200;

    private static final List<String> REFUSALS = List.of(
            "i'm sorry", "i am sorry", "i cannot", "i can't", "i can not", "i'm unable", "i am unable",
            "as an ai", "as a language model",
            "عذرا", "عذرًا", "عذراً", "آسف", "لا أستطيع", "لا يمكنني", "كنموذج لغوي", "بصفتي نموذج", "بصفتي ذكاء");

    private static final Pattern H2 = Pattern.compile("(?i)<h2[\\s>]");
    private static final Pattern PARAGRAPH = Pattern.compile("(?i)<p[\\s>]");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^\\s*#{1,6}\\s+\\S");

    private ArticleQualityCheck() {
    }

    /**
     * @return سبب الرفض بلغة المستخدم، أو {@code null} إن كان المقال مقبولًا
     */
    public static String problem(String html, String length) {
        if (html == null || html.isBlank()) {
            return "النموذج أعاد ردًّا فارغًا";
        }
        String text = SeoService.plainText(html);

        String opening = text.substring(0, Math.min(OPENING_CHARS, text.length())).toLowerCase(Locale.ROOT);
        for (String refusal : REFUSALS) {
            if (opening.contains(refusal)) {
                return "النموذج اعتذر عن كتابة المقال بدل كتابته";
            }
        }

        if (!PARAGRAPH.matcher(html).find() && MARKDOWN_HEADING.matcher(html).find()) {
            return "النموذج أعاد Markdown بدل HTML";
        }

        // رد مقطوع عند حد الرموز ينتهي وسط نص لا عند وسم إغلاق
        if (!html.trim().endsWith(">")) {
            return "المقال مقطوع قبل نهايته";
        }

        if (!H2.matcher(html).find()) {
            return "المقال بلا عناوين فرعية";
        }

        int words = wordCount(text);
        int minimum = (int) (ArticlePrompt.wordCount(length) * MIN_LENGTH_RATIO);
        if (words < minimum) {
            return "المقال قصير جدًا (" + words + " كلمة، والحد الأدنى " + minimum + ")";
        }
        return null;
    }

    /**
     * @throws IOException برسالة السبب إن لم يجتز المقال الفحص — نوع الاستثناء
     *                     نفسه الذي يرميه المزود عند الفشل، فيُسجَّل ويُعاد بالطريقة نفسها
     */
    public static void require(String html, String length) throws IOException {
        String problem = problem(html, length);
        if (problem != null) {
            throw new IOException("رُفض المقال في فحص الجودة: " + problem);
        }
    }

    static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        Matcher m = Pattern.compile("[\\p{L}\\p{N}]+").matcher(text);
        while (m.find()) {
            count++;
        }
        return count;
    }
}
