package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.SeoMetadata;
import com.hamzatech.publisher.provider.ContentGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeoServiceTest {

    private static final String BODY = "<h2>مقدمة</h2><p>قصير</p>"
            + "<p>الشاي الأخضر مشروب غني بمضادات الأكسدة، ويساعد على تحسين التركيز ودعم صحة القلب عند تناوله باعتدال.</p>";

    /** مزود وهمي يعيد ردًّا ثابتًا لطلب SEO. */
    private static ContentGenerator replying(String reply) {
        return new ContentGenerator() {
            public String id() { return "fake"; }
            public String displayName() { return "Fake"; }
            public boolean isConfigured() { return true; }
            public String generateArticle(String title, String targetLanguage) { return ""; }
            @Override
            public String complete(String prompt) { return reply; }
        };
    }

    private static ContentGenerator failing() {
        return new ContentGenerator() {
            public String id() { return "fake"; }
            public String displayName() { return "Fake"; }
            public boolean isConfigured() { return true; }
            public String generateArticle(String title, String targetLanguage) { return ""; }
            @Override
            public String complete(String prompt) throws IOException { throw new IOException("boom"); }
        };
    }

    @Test
    void aValidReply_isUsed_evenInsideCodeFences() {
        String reply = "```json\n{\"description\":\"وصف المقال\",\"tags\":[\"شاي\",\"صحة\"],\"imageAlt\":\"كوب شاي أخضر\"}\n```";

        SeoMetadata seo = SeoService.generate("الشاي الأخضر", BODY, "arabic", replying(reply));

        assertEquals("وصف المقال", seo.description());
        assertEquals(List.of("شاي", "صحة"), seo.tags());
        assertEquals("كوب شاي أخضر", seo.imageAlt());
    }

    @Test
    void tags_areCleanedDedupedAndCapped() {
        SeoMetadata seo = SeoService.parse("{\"tags\":[\"#Tea\",\"tea\",\" \",\"a\",\"b\",\"c\",\"d\",\"e\",\"f\","
                + "\"" + "x".repeat(60) + "\"]}");

        assertEquals(List.of("Tea", "a", "b", "c", "d", "e"), seo.tags());
    }

    @Test
    void aLongDescription_isCutAtAWordBoundary() {
        String longText = "word ".repeat(80);

        String cut = SeoService.truncate(longText, SeoService.DESCRIPTION_MAX);

        assertTrue(cut.length() <= SeoService.DESCRIPTION_MAX, cut);
        assertTrue(cut.endsWith("word…"), cut);
    }

    @Test
    void aFailingProvider_fallsBackToTheArticleItself() {
        SeoMetadata seo = SeoService.generate("الشاي الأخضر", BODY, "arabic", failing());

        // أول فقرة طويلة بما يكفي، لا الفقرة القصيرة قبلها
        assertTrue(seo.description().startsWith("الشاي الأخضر مشروب"), seo.description());
        assertEquals("الشاي الأخضر", seo.imageAlt());
        assertTrue(seo.tags().isEmpty());
    }

    @Test
    void aNonJsonReply_fallsBack() {
        SeoMetadata seo = SeoService.generate("t", BODY, "english", replying("Sorry, I can't help with that."));

        assertEquals("t", seo.imageAlt());
        assertTrue(seo.hasDescription());
    }

    @Test
    void aProviderWithoutCompletionSupport_fallsBack() {
        ContentGenerator unsupported = new ContentGenerator() {
            public String id() { return "plain"; }
            public String displayName() { return "Plain"; }
            public boolean isConfigured() { return true; }
            public String generateArticle(String title, String targetLanguage) { return ""; }
        };

        assertEquals("t", SeoService.generate("t", BODY, "english", unsupported).imageAlt());
    }

    @Test
    void missingFields_areFilledFromTheFallback() {
        SeoMetadata seo = SeoService.generate("العنوان", BODY, "arabic", replying("{\"tags\":[\"x\"]}"));

        assertEquals(List.of("x"), seo.tags());
        assertEquals("العنوان", seo.imageAlt());
        assertTrue(seo.hasDescription());
    }

    @Test
    void noGenerator_meansFallbackOnly() {
        SeoMetadata seo = SeoService.generate("t", "<p>too short</p>", "english", null);

        assertNull(seo.description());
        assertEquals("t", seo.imageAlt());
    }

    @Test
    void plainText_dropsTagsAndDecodesEntities() {
        assertEquals("A & B \"c\"", SeoService.plainText("<p>A &amp; <b>B</b> &quot;c&quot;</p>"));
    }

    @Test
    void prompt_asksForTheContentLanguage() {
        assertTrue(SeoService.prompt("t", "body", "arabic").contains("in Arabic"));
        assertTrue(SeoService.prompt("t", "body", "english").contains("in English"));
    }
}
