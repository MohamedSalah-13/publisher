package com.hamzatech.publisher.service;

import com.hamzatech.publisher.provider.ContentGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleIdeasServiceTest {

    /** مزود وهمي يعيد ردًّا ثابتًا ويحفظ آخر طلب. */
    private static final class Fake implements ContentGenerator {
        private final String reply;
        String lastPrompt;

        Fake(String reply) {
            this.reply = reply;
        }

        public String id() { return "fake"; }
        public String displayName() { return "Fake"; }
        public boolean isConfigured() { return true; }
        public String generateArticle(String title, String targetLanguage) { return ""; }

        @Override
        public String complete(String prompt) {
            lastPrompt = prompt;
            return reply;
        }
    }

    @Test
    void aJsonArray_isReadInOrder() throws IOException {
        List<String> titles = TitleIdeasService.generate("السياحة في تركيا", 5, "arabic",
                new Fake("[\"أفضل 10 مدن سياحية في تركيا\", \"دليل السفر إلى إسطنبول للمبتدئين\"]"));

        assertEquals(List.of("أفضل 10 مدن سياحية في تركيا", "دليل السفر إلى إسطنبول للمبتدئين"), titles);
    }

    @Test
    void aJsonArrayInsideFences_isStillRead() {
        assertEquals(List.of("A", "B"), TitleIdeasService.parse("```json\n[\"A\",\"B\"]\n```", 10));
    }

    @Test
    void aNumberedList_isAcceptedWhenTheModelIgnoresTheFormat() {
        String reply = "Here are some titles:\n1. First title\n2) \"Second title\"\n- **Third title**\n٤. رابع عنوان";

        List<String> titles = TitleIdeasService.parse(reply, 10);

        assertTrue(titles.containsAll(List.of("First title", "Second title", "Third title", "رابع عنوان")), titles.toString());
    }

    @Test
    void duplicatesAndOverlongTitles_areDropped_andTheCountIsCapped() {
        String reply = "[\"Same\", \"same\", \"" + "x".repeat(200) + "\", \"B\", \"C\", \"D\"]";

        assertEquals(List.of("Same", "B", "C"), TitleIdeasService.parse(reply, 3));
    }

    @Test
    void anEmptyReply_isAnError_notAnEmptyFile() {
        assertThrows(IOException.class,
                () -> TitleIdeasService.generate("topic", 5, "english", new Fake("[]")));
    }

    @Test
    void theRequestedCount_isClampedAndSentInTheRequestedLanguage() throws IOException {
        Fake fake = new Fake("[\"t\"]");

        TitleIdeasService.generate("topic", 500, "english", fake);

        assertTrue(fake.lastPrompt.contains("Suggest " + TitleIdeasService.MAX_COUNT + " "), fake.lastPrompt);
        assertTrue(fake.lastPrompt.contains("in English"));
    }

    @Test
    void aBlankTopic_isRejectedBeforeCallingTheModel() {
        assertThrows(IllegalArgumentException.class,
                () -> TitleIdeasService.generate("  ", 5, "arabic", new Fake("[\"t\"]")));
    }

    @Test
    void clean_stripsNumberingQuotesAndTrailingCommas() {
        assertEquals("عنوان", TitleIdeasService.clean("  3. «عنوان»،"));
        assertEquals("", TitleIdeasService.clean("],"));
    }
}
