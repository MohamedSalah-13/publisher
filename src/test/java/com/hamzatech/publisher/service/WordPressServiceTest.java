package com.hamzatech.publisher.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordPressServiceTest {

    private final WordPressService wordPressService = new WordPressService();

    @Test
    void isSecureUrl_acceptsHttps() {
        assertTrue(WordPressService.isSecureUrl("https://example.com"));
        assertTrue(WordPressService.isSecureUrl("https://example.com/blog"));
        assertTrue(WordPressService.isSecureUrl("  https://example.com  "));
    }

    @Test
    void isSecureUrl_rejectsPlainHttp() {
        assertFalse(WordPressService.isSecureUrl("http://example.com"));
        assertFalse(WordPressService.isSecureUrl("http://example.com/blog"));
    }

    @Test
    void isSecureUrl_allowsLoopbackOverHttp() {
        // حركة المرور لا تغادر الجهاز، فلا يوجد متنصت ممكن
        assertTrue(WordPressService.isSecureUrl("http://localhost:8080"));
        assertTrue(WordPressService.isSecureUrl("http://127.0.0.1"));
    }

    @Test
    void isSecureUrl_allowsAHostnameThatResolvesToLoopback() {
        // المقارنة الحرفية بـ"localhost" وحدها كانت تمنع التطوير المحلي خلف اسم
        // مستعار في ملف hosts، وهو الشكل الذي يظهر به MockWebServer على هذا الجهاز
        assertTrue(WordPressService.isSecureUrl("http://wordpress.localhost:8080"));
        assertTrue(WordPressService.isSecureUrl("http://my-site.localhost"));
    }

    @Test
    void isSecureUrl_stillRejectsARealHostOverHttp() {
        assertFalse(WordPressService.isSecureUrl("http://example.com"));
        assertFalse(WordPressService.isSecureUrl("http://not-a-real-host.invalid"));
    }

    @Test
    void isSecureUrl_rejectsUnusableInput() {
        assertFalse(WordPressService.isSecureUrl(null));
        assertFalse(WordPressService.isSecureUrl("   "));
        assertFalse(WordPressService.isSecureUrl("example.com"));   // بلا بروتوكول
        assertFalse(WordPressService.isSecureUrl("ftp://example.com"));
    }

    @Test
    void normalizeUrl_assumesHttpsWhenSchemeMissing() {
        assertEquals("https://example.com", WordPressService.normalizeUrl("example.com"));
        assertEquals("https://example.com", WordPressService.normalizeUrl("  example.com/  "));
    }

    @Test
    void normalizeUrl_keepsExplicitSchemeAndStripsTrailingSlashes() {
        assertEquals("https://example.com", WordPressService.normalizeUrl("https://example.com///"));
        assertEquals("http://example.com", WordPressService.normalizeUrl("http://example.com/"));
        assertNull(WordPressService.normalizeUrl(null));
    }

    @Test
    void publishPost_refusesToSendCredentialsOverHttp() {
        IOException e = assertThrows(IOException.class, () -> wordPressService.publishPost(
                "http://example.com", "admin", "pass word", "Title", "<p>Body</p>", null));
        assertTrue(e.getMessage().contains("https://"), e.getMessage());
    }

    @Test
    void testConnection_refusesToSendCredentialsOverHttp() {
        assertThrows(IOException.class, () -> wordPressService.testConnection(
                "http://example.com", "admin", "pass word"));
    }

    @Test
    void publishPost_rejectsUnparseableUrl() {
        assertThrows(IOException.class, () -> wordPressService.publishPost(
                "not a url", "admin", "pass", "Title", "<p>Body</p>", null));
    }
}
