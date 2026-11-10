package com.hamzatech.publisher.model;

/**
 * مقال مُولَّد جاهز للنشر، قبل إرساله إلى أي منصة.
 * <p>
 * الجسم وصورة الرأس منفصلان عمدًا: ووردبريس يقبل صورة بارزة مستقلة، فلو دُمجت
 * الصورة في الجسم لظهرت مرتين في القوالب التي تعرض الصورة البارزة. أما Blogger
 * فلا مفهوم للصورة البارزة عنده، فيأخذ {@link #contentHtml()} المركّب.
 *
 * @param originalTitle العنوان كما ورد في ملف Excel
 * @param publishTitle  العنوان المنشور فعلًا (قد يكون مترجمًا)
 * @param bodyHtml      جسم المقال وحده، بلا صورة الرأس
 * @param imageUrl      رابط صورة الرأس
 * @param customSlug    الرابط المخصص، أو {@code null} لترك المنصة تولّده
 * @param seo           وصف الميتا والوسوم والنص البديل للصورة؛ لا {@code null}
 */
public record ArticleContent(String originalTitle,
                              String publishTitle,
                              String bodyHtml,
                              String imageUrl,
                              String customSlug,
                              SeoMetadata seo) {

    public ArticleContent {
        seo = seo == null ? SeoMetadata.empty() : seo;
    }

    public ArticleContent(String originalTitle, String publishTitle, String bodyHtml,
                          String imageUrl, String customSlug) {
        this(originalTitle, publishTitle, bodyHtml, imageUrl, customSlug, SeoMetadata.empty());
    }

    /** النص البديل لصورة الرأس: ما ولّده SEO، وإلا العنوان المنشور. */
    public String imageAlt() {
        return seo.hasImageAlt() ? seo.imageAlt() : publishTitle;
    }

    /**
     * الجسم مسبوقًا بصورة الرأس داخله، لمنصة لا تدعم صورة بارزة منفصلة.
     */
    public String contentHtml() {
        if (imageUrl == null || imageUrl.isBlank()) {
            return bodyHtml;
        }
        return "<div style=\"text-align: center; margin-bottom: 20px;\">"
                + "<img src=\"" + escapeHtmlAttribute(imageUrl) + "\""
                + " alt=\"" + escapeHtmlAttribute(imageAlt()) + "\""
                + " style=\"max-width:100%; height:auto;\"/>"
                + "</div>"
                + bodyHtml;
    }

    /**
     * تهريب النص قبل وضعه داخل خاصية HTML.
     */
    private static String escapeHtmlAttribute(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
