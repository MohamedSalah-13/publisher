package com.hamzatech.publisher.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TitleGeneratorDialogTest {

    @Test
    void fileName_keepsArabicAndDropsCharactersWindowsRejects() {
        assertEquals("السياحة-في-تركيا.xlsx", TitleGeneratorDialog.fileNameFor("  السياحة في تركيا "));
        assertEquals("a-b-c.xlsx", TitleGeneratorDialog.fileNameFor("a/b:c?"));
        assertEquals("titles.xlsx", TitleGeneratorDialog.fileNameFor("  "));
    }
}
