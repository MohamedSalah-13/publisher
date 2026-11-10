package com.hamzatech.publisher.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticlePromptTest {

    @Test
    void arabicPrompt_carriesTheTitleAndTheRequestedLength() {
        ArticlePrompt prompt = ArticlePrompt.forArticle("فوائد الشاي الأخضر", "arabic",
                new ArticlePrompt.Options("long", false, null));

        assertTrue(prompt.user().contains("فوائد الشاي الأخضر"));
        assertTrue(prompt.system().contains("2000"), prompt.system());
        assertFalse(prompt.system().contains("الأسئلة الشائعة"));
    }

    @Test
    void englishPrompt_isWrittenInEnglish() {
        ArticlePrompt prompt = ArticlePrompt.forArticle("Green tea benefits", "english",
                new ArticlePrompt.Options("short", false, null));

        assertTrue(prompt.system().startsWith("You are"));
        assertTrue(prompt.system().contains("600"));
        assertTrue(prompt.user().endsWith("Green tea benefits"));
    }

    @Test
    void faqSection_isRequestedOnlyWhenEnabled() {
        ArticlePrompt withFaq = ArticlePrompt.forArticle("t", "english",
                new ArticlePrompt.Options("medium", true, null));

        assertTrue(withFaq.system().contains("Frequently Asked Questions"));
    }

    @Test
    void ownerInstructions_areAppendedVerbatim() {
        ArticlePrompt prompt = ArticlePrompt.forArticle("t", "arabic",
                new ArticlePrompt.Options("medium", false, "  اكتب بأسلوب ودّي  "));

        assertTrue(prompt.system().endsWith("اكتب بأسلوب ودّي"), prompt.system());
    }

    @Test
    void blankInstructions_addNothing() {
        ArticlePrompt plain = ArticlePrompt.forArticle("t", "arabic",
                new ArticlePrompt.Options("medium", false, null));
        ArticlePrompt blank = ArticlePrompt.forArticle("t", "arabic",
                new ArticlePrompt.Options("medium", false, "   "));

        assertEquals(plain, blank);
    }

    @Test
    void unknownLength_fallsBackToMedium() {
        assertEquals(1200, ArticlePrompt.wordCount("huge"));
        assertEquals(1200, ArticlePrompt.wordCount(null));
        assertEquals(ArticlePrompt.maxOutputTokens("medium"), ArticlePrompt.maxOutputTokens("??"));
    }

    @Test
    void longerArticles_getALargerTokenBudget() {
        assertTrue(ArticlePrompt.maxOutputTokens("short") < ArticlePrompt.maxOutputTokens("medium"));
        assertTrue(ArticlePrompt.maxOutputTokens("medium") < ArticlePrompt.maxOutputTokens("long"));
    }
}
