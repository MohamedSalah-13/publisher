package com.hamzatech.publisher.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleContentTest {

    private static ArticleContent article(String imageUrl, String title) {
        return new ArticleContent("original", title, "<div>body</div>", imageUrl, "slug");
    }

    @Test
    void bodyHtmlCarriesNoImage_soWordPressCanUseAFeaturedOne() {
        // لو حملت الصورة لظهرت مرتين في القوالب التي تعرض الصورة البارزة
        assertFalse(article("https://img.example.com/a.jpg", "T").bodyHtml().contains("<img"));
    }

    @Test
    void contentHtmlPrependsTheImage_forPlatformsWithoutFeaturedImages() {
        String html = article("https://img.example.com/a.jpg", "T").contentHtml();

        assertTrue(html.contains("<img src=\"https://img.example.com/a.jpg\""), html);
        assertTrue(html.endsWith("<div>body</div>"), html);
        assertTrue(html.indexOf("<img") < html.indexOf("body"), "the image comes first: " + html);
    }

    @Test
    void contentHtmlIsJustTheBodyWhenThereIsNoImage() {
        assertEquals("<div>body</div>", article(null, "T").contentHtml());
        assertEquals("<div>body</div>", article("   ", "T").contentHtml());
    }

    @Test
    void theImageUrlIsEscaped() {
        // روابط مزودي الصور تحمل استعلامات بـ& وقد تحمل اقتباسًا يكسر الوسم
        String html = article("https://img.example.com/a.jpg?w=1&h=2\"onerror=\"x", "T").contentHtml();

        assertTrue(html.contains("&amp;"), html);
        assertFalse(html.contains("\"onerror"), "an unescaped quote would break out of the attribute: " + html);
    }

    @Test
    void theTitleIsEscapedInTheAltText() {
        String html = article("https://img.example.com/a.jpg", "A \"quoted\" & <tagged> title").contentHtml();

        assertTrue(html.contains("&quot;"), html);
        assertTrue(html.contains("&amp;"), html);
        assertTrue(html.contains("&lt;tagged&gt;"), html);
    }

    @Test
    void theImageAltComesFromSeo_whenGenerated() {
        ArticleContent withSeo = new ArticleContent("o", "T", "<div>body</div>", "https://img.example.com/a.jpg",
                "slug", new SeoMetadata("d", java.util.List.of(), "كوب شاي أخضر"));

        assertTrue(withSeo.contentHtml().contains("alt=\"كوب شاي أخضر\""), withSeo.contentHtml());
    }

    @Test
    void theImageAltFallsBackToTheTitle() {
        assertTrue(article("https://img.example.com/a.jpg", "Title").contentHtml().contains("alt=\"Title\""));
        assertTrue(article(null, "T").seo().tags().isEmpty(), "seo is never null");
    }
}
