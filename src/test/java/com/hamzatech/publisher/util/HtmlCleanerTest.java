package com.hamzatech.publisher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HtmlCleanerTest {

    @Test
    void stripsMarkdownCodeFences() {
        assertEquals("<h2>A</h2><p>b</p>", HtmlCleaner.clean("```html\n<h2>A</h2><p>b</p>\n```"));
    }

    @Test
    void dropsAPreambleBeforeTheFirstTag() {
        assertEquals("<p>المقال</p>", HtmlCleaner.clean("إليك المقال المطلوب:\n<p>المقال</p>"));
    }

    @Test
    void dropsATrailingNoteAfterTheLastClosingTag() {
        assertEquals("<p>body</p>", HtmlCleaner.clean("<p>body</p>\nHope this helps!"));
    }

    @Test
    void leavesCleanHtmlUntouched() {
        String html = "<h2>Title</h2>\n<p>Text</p>\n<ul><li>x</li></ul>";
        assertEquals(html, HtmlCleaner.clean(html));
    }

    @Test
    void passesNullAndBlankThrough() {
        assertNull(HtmlCleaner.clean(null));
        assertEquals("  ", HtmlCleaner.clean("  "));
    }
}
