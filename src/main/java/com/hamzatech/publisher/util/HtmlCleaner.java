package com.hamzatech.publisher.util;

/**
 * ينظّف رد نموذج الذكاء الاصطناعي ليبقى HTML المقال وحده.
 * <p>
 * النماذج تضيف أحيانًا رغم التعليمات أسوار Markdown ({@code ```html}) أو جملة
 * تمهيدية ("إليك المقال") أو ملاحظة ختامية، وكلها كانت تُنشر حرفيًا داخل المقال.
 * كان هذا التنظيف خاصًا بـ Gemini، وصار مشتركًا لكل المزودين.
 */
public final class HtmlCleaner {

    private static final String[] HTML_STARTS = {"<div", "<p", "<article", "<section", "<h1", "<h2", "<h3", "<ul", "<ol"};
    private static final String[] HTML_ENDS = {"</div>", "</p>", "</article>", "</section>", "</ul>", "</ol>"};

    private HtmlCleaner() {
    }

    public static String clean(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        // إزالة أسوار Markdown إن وُجدت
        text = text.replaceAll("```html\\s*", "");
        text = text.replaceAll("```\\s*", "");

        // حذف أي نص قبل أول وسم HTML حقيقي
        int firstHtmlTag = -1;
        for (String tag : HTML_STARTS) {
            int pos = text.indexOf(tag);
            if (pos >= 0 && (firstHtmlTag == -1 || pos < firstHtmlTag)) {
                firstHtmlTag = pos;
            }
        }
        if (firstHtmlTag > 0) {
            text = text.substring(firstHtmlTag);
        }

        // حذف نص عادي (لا HTML) بعد آخر وسم إغلاق
        int lastClosingTag = -1;
        for (String tag : HTML_ENDS) {
            int pos = text.lastIndexOf(tag);
            if (pos >= 0 && pos + tag.length() > lastClosingTag) {
                lastClosingTag = pos + tag.length();
            }
        }
        if (lastClosingTag > 0 && lastClosingTag < text.length() - 1) {
            String afterTag = text.substring(lastClosingTag).trim();
            if (!afterTag.isEmpty() && !afterTag.startsWith("<")) {
                text = text.substring(0, lastClosingTag);
            }
        }

        return text.trim();
    }
}
