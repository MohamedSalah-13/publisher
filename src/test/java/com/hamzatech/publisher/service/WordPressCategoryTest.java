package com.hamzatech.publisher.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * يفحص ما يُرسَل إلى ووردبريس فعلًا، لا ما نظن أننا نرسله.
 * <p>
 * يعمل على {@code http://localhost} وهو الاستثناء الوحيد الذي يسمح به
 * {@link WordPressService#isSecureUrl}: حركة الجهاز المحلي لا تغادره.
 */
class WordPressCategoryTest {

    private static final String USER = "admin";
    private static final String PASSWORD = "abcd EFGH ijkl";

    private MockWebServer server;
    private WordPressService service;
    private String siteUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        siteUrl = server.url("/").toString().replaceAll("/$", "");
        service = new WordPressService();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    @Test
    void listCategories_returnsNamesWithTheirCounts() throws Exception {
        server.enqueue(json(200, "[{\"id\":3,\"name\":\"سياحة\",\"count\":12},"
                + "{\"id\":7,\"name\":\"تقنية\",\"count\":4}]"));

        Map<String, Integer> categories = service.listCategories(siteUrl, USER, PASSWORD);

        assertEquals(Map.of("سياحة", 12, "تقنية", 4), categories);
    }

    @Test
    void listCategories_asksForEmptyOnesToo() throws Exception {
        // تصنيف فارغ هو غالبًا ما أنشأه المستخدم ليملأه؛ إخفاؤه يجعله غير قابل للاختيار
        server.enqueue(json(200, "[]"));

        service.listCategories(siteUrl, USER, PASSWORD);

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("hide_empty=false"), request.getPath());
        assertTrue(request.getPath().startsWith("/wp-json/wp/v2/categories"), request.getPath());
    }

    @Test
    void listCategories_sendsTheApplicationPasswordWithoutItsSpaces() throws Exception {
        server.enqueue(json(200, "[]"));

        service.listCategories(siteUrl, USER, PASSWORD);

        String auth = server.takeRequest().getHeader("Authorization");
        assertTrue(auth.startsWith("Basic "), auth);
        String decoded = new String(java.util.Base64.getDecoder().decode(auth.substring(6)));
        assertEquals("admin:abcdEFGHijkl", decoded);
    }

    @Test
    void listCategories_reportsAnErrorResponse() {
        server.enqueue(json(401, "{\"message\":\"Sorry, you are not allowed to do that.\"}"));

        IOException e = assertThrows(IOException.class, () -> service.listCategories(siteUrl, USER, PASSWORD));
        assertTrue(e.getMessage().contains("401"), e.getMessage());
    }

    @Test
    void findOrCreateCategoryId_returnsTheExistingId() throws Exception {
        server.enqueue(json(200, "[{\"id\":9,\"name\":\"سياحة\",\"count\":2}]"));

        assertEquals(9, service.findOrCreateCategoryId(siteUrl, USER, PASSWORD, "سياحة"));
        assertEquals(1, server.getRequestCount(), "an existing category must not be created again");
    }

    @Test
    void findOrCreateCategoryId_ignoresAPartialMatch() throws Exception {
        // بحث ووردبريس جزئي: البحث عن "أخبار" يعيد "أخبار محلية" أيضًا
        server.enqueue(json(200, "[{\"id\":11,\"name\":\"أخبار محلية\",\"count\":5}]"));
        server.enqueue(json(201, "{\"id\":12,\"name\":\"أخبار\"}"));

        assertEquals(12, service.findOrCreateCategoryId(siteUrl, USER, PASSWORD, "أخبار"));

        server.takeRequest();
        RecordedRequest create = server.takeRequest();
        assertEquals("POST", create.getMethod());
    }

    @Test
    void findOrCreateCategoryId_matchesIgnoringCaseAndSpacing() throws Exception {
        server.enqueue(json(200, "[{\"id\":4,\"name\":\"Travel\",\"count\":1}]"));

        assertEquals(4, service.findOrCreateCategoryId(siteUrl, USER, PASSWORD, "  travel "));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void findOrCreateCategoryId_createsWhenMissing() throws Exception {
        server.enqueue(json(200, "[]"));
        server.enqueue(json(201, "{\"id\":21,\"name\":\"جديد\"}"));

        assertEquals(21, service.findOrCreateCategoryId(siteUrl, USER, PASSWORD, "جديد"));

        server.takeRequest();
        RecordedRequest create = server.takeRequest();
        assertEquals("POST", create.getMethod());
        assertTrue(create.getBody().readUtf8().contains("\"name\""), "the create call must carry the name");
    }

    @Test
    void findOrCreateCategoryId_usesTheIdWordPressReturnsForADuplicate() throws Exception {
        // سباق بين دفعتين تُنشئان التصنيف نفسه: ووردبريس يرفض ويعيد معرّف القائم
        server.enqueue(json(200, "[]"));
        server.enqueue(json(400, "{\"code\":\"term_exists\",\"message\":\"A term already exists\","
                + "\"data\":{\"status\":400,\"term_id\":33}}"));

        assertEquals(33, service.findOrCreateCategoryId(siteUrl, USER, PASSWORD, "مكرر"));
    }

    @Test
    void findOrCreateCategoryId_reportsARealCreateFailure() {
        server.enqueue(json(200, "[]"));
        server.enqueue(json(403, "{\"code\":\"rest_cannot_create\",\"message\":\"Not allowed\"}"));

        IOException e = assertThrows(IOException.class,
                () -> service.findOrCreateCategoryId(siteUrl, USER, PASSWORD, "ممنوع"));
        assertTrue(e.getMessage().contains("403"), e.getMessage());
    }

    @Test
    void publishPost_attachesTheCategory() throws Exception {
        server.enqueue(json(201, "{\"id\":5,\"link\":\"https://example.com/hello\"}"));

        String url = service.publishPost(siteUrl, USER, PASSWORD, "Title", "<p>Body</p>", "hello", 42);

        assertEquals("https://example.com/hello", url);
        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"categories\":[42]"), body);
        assertTrue(body.contains("\"slug\":\"hello\""), body);
        assertTrue(body.contains("\"status\":\"publish\""), body);
    }

    @Test
    void publishPost_withoutACategorySendsNoCategoriesField() throws Exception {
        server.enqueue(json(201, "{\"id\":5,\"link\":\"https://example.com/hello\"}"));

        service.publishPost(siteUrl, USER, PASSWORD, "Title", "<p>Body</p>", null, null);

        String body = server.takeRequest().getBody().readUtf8();
        assertFalse(body.contains("categories"), body);
        assertFalse(body.contains("slug"), "a null slug must be left to WordPress: " + body);
    }
}
