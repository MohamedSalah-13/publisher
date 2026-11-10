package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.PublishLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalLinksTest {

    private static final String BLOG = "blog-1";

    private static PublishLog published(String title, String url, String blogId, int daysAgo) {
        PublishLog log = PublishLog.success(title, "Blog", blogId, "id", url, null);
        log.setPublishDate(LocalDateTime.now().minusDays(daysAgo));
        return log;
    }

    private static InternalLinks.Candidate candidate(String title, int daysAgo) {
        return new InternalLinks.Candidate(title, "https://b.example.com/" + title.hashCode(),
                LocalDateTime.now().minusDays(daysAgo));
    }

    @Test
    void tokens_dropStopWordsAndTheDefiniteArticle() {
        // "السياحة" و"سياحة" كلمة واحدة، و"أفضل"/"في" لا تدلان على موضوع
        assertEquals(java.util.Set.of("سياحة", "تركيا"), InternalLinks.tokens("أفضل السياحة في تركيا"));
        assertEquals(java.util.Set.of("travel", "turkey"), InternalLinks.tokens("The best travel guide for Turkey"));
    }

    @Test
    void candidates_keepOnlyLivePostsOnTheSameBlogInTheSameLanguage() {
        PublishLog draft = published("مسودة عن تركيا", "https://b/draft", BLOG, 1);
        draft.setDraft(true);
        List<PublishLog> logs = List.of(
                published("السياحة في إسطنبول", "https://b/1", BLOG, 1),
                published("Istanbul travel", "https://b/2", BLOG, 1),          // لغة أخرى
                published("السياحة في أنطاليا", "https://other/3", "blog-2", 1), // مدونة أخرى
                draft,                                                           // مسودة بلا رابط عام
                PublishLog.failure("مقال فاشل عن تركيا", "Blog", BLOG, null, "x"),
                published("السياحة في تركيا", "https://b/self", BLOG, 1),      // المقال نفسه
                published("السياحة في إسطنبول", "https://b/1", BLOG, 3));      // تكرار الرابط

        List<InternalLinks.Candidate> result = InternalLinks.candidates(logs, BLOG, "  السياحة في تركيا ", false);

        assertEquals(List.of("https://b/1"), result.stream().map(InternalLinks.Candidate::url).toList());
    }

    @Test
    void pickRelated_prefersMoreSharedWords_thenTheNewest() {
        List<InternalLinks.Candidate> pool = List.of(
                candidate("السياحة في إسطنبول", 10),
                candidate("تكلفة السياحة في تركيا", 30),
                candidate("مطاعم إسطنبول", 1),
                candidate("طبخ الكسكسي المغربي", 1));

        List<String> picked = InternalLinks.pickRelated("السياحة في تركيا وإسطنبول", pool, 3)
                .stream().map(InternalLinks.Candidate::title).toList();

        assertEquals(List.of("السياحة في إسطنبول", "تكلفة السياحة في تركيا", "مطاعم إسطنبول"), picked);
    }

    @Test
    void pickRelated_returnsNothingWhenNothingIsRelated() {
        assertTrue(InternalLinks.pickRelated("أفضل لغات البرمجة",
                List.of(candidate("السياحة في تركيا", 1)), 3).isEmpty());
    }

    @Test
    void pickRelated_ignoresStopWordOnlyMatches() {
        // "دليل" و"أفضل" مشتركتان لكنهما لا تدلان على صلة
        assertTrue(InternalLinks.pickRelated("أفضل دليل للبرمجة",
                List.of(candidate("أفضل دليل للسفر", 1)), 3).isEmpty());
    }

    @Test
    void appendSection_escapesTitlesAndUrls() {
        String html = InternalLinks.appendSection("<p>body</p>",
                List.of(new InternalLinks.Candidate("A <b> & \"C\"", "https://b/x?a=1&b=2", null)), true);

        assertTrue(html.startsWith("<p>body</p>"));
        assertTrue(html.contains("<h3>Read also</h3>"));
        assertTrue(html.contains("href=\"https://b/x?a=1&amp;b=2\""), html);
        assertTrue(html.contains(">A &lt;b&gt; &amp; &quot;C&quot;</a>"), html);
        assertFalse(html.contains("<b>"));
    }

    @Test
    void arabicArticles_getAnArabicHeading() {
        assertTrue(InternalLinks.appendSection("", List.of(candidate("x", 1)), false).contains("اقرأ أيضًا"));
    }

    @Test
    void noTarget_meansNoLinks() {
        String body = "<p>body</p>";
        assertSame(body, InternalLinks.addTo(body, "t", "t", null, "arabic"));
    }

    @Test
    void isEnglish_followsTheDominantScript() {
        assertTrue(InternalLinks.isEnglish("SEO tips for Blogger"));
        assertFalse(InternalLinks.isEnglish("نصائح SEO لبلوجر"));
    }
}
