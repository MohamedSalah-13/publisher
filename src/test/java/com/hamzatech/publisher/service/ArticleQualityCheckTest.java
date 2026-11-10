package com.hamzatech.publisher.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleQualityCheckTest {

    /** مقال سليم بعدد كلمات محدد. */
    private static String article(int words) {
        return "<h2>مقدمة</h2><p>" + "كلمة ".repeat(words).trim() + "</p><h2>خاتمة</h2><p>نهاية.</p>";
    }

    @Test
    void aProperArticle_passes() {
        assertNull(ArticleQualityCheck.problem(article(700), "medium"));
    }

    @Test
    void anEmptyReply_isRejected() {
        assertNotNull(ArticleQualityCheck.problem("  ", "medium"));
        assertNotNull(ArticleQualityCheck.problem(null, "medium"));
    }

    @Test
    void anArabicApology_isRejected() {
        String html = "<p>عذرًا، لا أستطيع كتابة هذا المقال.</p>" + article(700);

        assertTrue(ArticleQualityCheck.problem(html, "medium").contains("اعتذر"));
    }

    @Test
    void anEnglishRefusal_isRejected() {
        String html = "<p>I'm sorry, but as an AI I cannot write that.</p>" + article(700);

        assertNotNull(ArticleQualityCheck.problem(html, "medium"));
    }

    @Test
    void apologeticWordsLaterInTheArticle_areFine() {
        // مقال عن آداب الاعتذار يذكر الكلمة؛ الفحص على أول الرد فقط
        String html = article(700) + "<p>ويقول البعض: عذرًا على التأخير.</p>";

        assertNull(ArticleQualityCheck.problem(html, "medium"));
    }

    @Test
    void markdownInsteadOfHtml_isRejected() {
        String markdown = "## مقدمة\n" + "كلمة ".repeat(700) + "\n## خاتمة\nنهاية.";

        assertTrue(ArticleQualityCheck.problem(markdown, "medium").contains("Markdown"));
    }

    @Test
    void aTruncatedArticle_isRejected() {
        String cut = "<h2>مقدمة</h2><p>" + "كلمة ".repeat(700) + "وفي النهاية يمكن";

        assertTrue(ArticleQualityCheck.problem(cut, "medium").contains("مقطوع"));
    }

    @Test
    void anArticleWithoutSubheadings_isRejected() {
        String flat = "<p>" + "كلمة ".repeat(700) + "</p>";

        assertTrue(ArticleQualityCheck.problem(flat, "medium").contains("عناوين"));
    }

    @Test
    void aShortArticle_isRejectedAgainstTheChosenLength() {
        // متوسط = 1200 كلمة، فالحد الأدنى 600
        assertTrue(ArticleQualityCheck.problem(article(300), "medium").contains("قصير"));
        // والمقال نفسه مقبول إن كان الطول المختار "قصير" (600، فالحد الأدنى 300)
        assertNull(ArticleQualityCheck.problem(article(300), "short"));
    }

    @Test
    void wordCount_countsArabicAndEnglishWords_notTags() {
        assertEquals(4, ArticleQualityCheck.wordCount("مرحبا بك  in Publisher"));
        assertEquals(0, ArticleQualityCheck.wordCount(" , . "));
    }

    @Test
    void require_throwsWithTheReason() {
        IOException e = assertThrows(IOException.class, () -> ArticleQualityCheck.require("<p>x</p>", "medium"));
        assertTrue(e.getMessage().startsWith("رُفض المقال في فحص الجودة"), e.getMessage());
    }

    // --- إعادة التوليد في ArticlePublishingService ---

    private static ArticlePublishingService.ArticleSource replies(Deque<String> replies, int[] calls) {
        return () -> {
            calls[0]++;
            return replies.removeFirst();
        };
    }

    @Test
    void aGoodFirstReply_isUsedWithoutRegenerating() throws IOException {
        int[] calls = {0};
        String good = article(700);

        String result = ArticlePublishingService.generateCheckedArticle(
                replies(new ArrayDeque<>(List.of(good)), calls), "medium", true);

        assertEquals(good, result);
        assertEquals(1, calls[0]);
    }

    @Test
    void aBadFirstReply_isRegeneratedOnce() throws IOException {
        int[] calls = {0};
        String good = article(700);

        String result = ArticlePublishingService.generateCheckedArticle(
                replies(new ArrayDeque<>(List.of("<p>عذرًا</p>", good)), calls), "medium", true);

        assertEquals(good, result);
        assertEquals(2, calls[0]);
    }

    @Test
    void twoBadReplies_failTheArticle_withoutAThirdAttempt() {
        int[] calls = {0};

        assertThrows(IOException.class, () -> ArticlePublishingService.generateCheckedArticle(
                replies(new ArrayDeque<>(List.of("<p>عذرًا</p>", "<p>قصير</p>", article(700))), calls),
                "medium", true));
        assertEquals(2, calls[0]);
    }

    @Test
    void withTheCheckOff_anyReplyPasses() throws IOException {
        int[] calls = {0};

        assertEquals("<p>x</p>", ArticlePublishingService.generateCheckedArticle(
                replies(new ArrayDeque<>(List.of("<p>x</p>")), calls), "medium", false));
    }
}
