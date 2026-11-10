package com.hamzatech.publisher.service;

import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * رفع صورة الرأس إلى مكتبة وسائط ووردبريس، مفحوصًا ضد خادم حقيقي.
 */
class WordPressMediaTest {

    private static final String USER = "admin";
    private static final String PASSWORD = "abcd EFGH";
    private static final byte[] IMAGE_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5};

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
        return new MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json").setBody(body);
    }

    private static MockResponse image(String contentType) {
        Buffer buffer = new Buffer();
        buffer.write(IMAGE_BYTES);
        return new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", contentType).setBody(buffer);
    }

    /** الصورة تُنزَّل من الخادم نفسه، فيكفي خادم واحد للاختبار. */
    private String imageUrl() {
        return server.url("/photo?w=1600&fit=crop").toString();
    }

    @Test
    void uploadMedia_sendsTheDownloadedBytesAndReturnsTheId() throws Exception {
        server.enqueue(image("image/jpeg"));
        server.enqueue(json(201, "{\"id\":88,\"source_url\":\"https://example.com/x.jpg\"}"));

        int id = service.uploadMedia(siteUrl, USER, PASSWORD, imageUrl(), "My Title");

        assertEquals(88, id);
        server.takeRequest(); // التنزيل
        RecordedRequest upload = server.takeRequest();
        assertEquals("POST", upload.getMethod());
        assertEquals("/wp-json/wp/v2/media", upload.getPath());
        assertArrayEquals(IMAGE_BYTES, upload.getBody().readByteArray(),
                "the exact downloaded bytes must be forwarded");
    }

    @Test
    void uploadMedia_sendsAFileNameSoWordPressKnowsTheFormat() throws Exception {
        // ووردبريس يشتق الصيغة من اسم الملف، لا من نوع المحتوى وحده
        server.enqueue(image("image/png"));
        server.enqueue(json(201, "{\"id\":1}"));

        service.uploadMedia(siteUrl, USER, PASSWORD, imageUrl(), "t");

        server.takeRequest();
        String disposition = server.takeRequest().getHeader("Content-Disposition");
        assertTrue(disposition.startsWith("attachment; filename="), disposition);
        assertTrue(disposition.endsWith(".png\""), "extension must follow the content type: " + disposition);
    }

    @Test
    void uploadMedia_derivesTheExtensionFromTheContentTypeNotTheUrl() throws Exception {
        // روابط مزودي الصور تحمل استعلامات ولا تنتهي بامتداد
        server.enqueue(image("image/webp"));
        server.enqueue(json(201, "{\"id\":1}"));

        service.uploadMedia(siteUrl, USER, PASSWORD, imageUrl(), "t");

        server.takeRequest();
        assertTrue(server.takeRequest().getHeader("Content-Disposition").endsWith(".webp\""));
    }

    @Test
    void uploadMedia_authenticates() throws Exception {
        server.enqueue(image("image/jpeg"));
        server.enqueue(json(201, "{\"id\":1}"));

        service.uploadMedia(siteUrl, USER, PASSWORD, imageUrl(), "t");

        server.takeRequest();
        assertTrue(server.takeRequest().getHeader("Authorization").startsWith("Basic "));
    }

    @Test
    void uploadMedia_reportsAFailedDownload() {
        server.enqueue(new MockResponse().setResponseCode(404));

        IOException e = assertThrows(IOException.class,
                () -> service.uploadMedia(siteUrl, USER, PASSWORD, imageUrl(), "t"));
        assertTrue(e.getMessage().contains("404"), e.getMessage());
    }

    @Test
    void uploadMedia_rejectsAnEmptyDownload() {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "image/jpeg"));

        assertThrows(IOException.class, () -> service.uploadMedia(siteUrl, USER, PASSWORD, imageUrl(), "t"));
    }

    @Test
    void uploadMedia_reportsARejectedUpload() {
        server.enqueue(image("image/jpeg"));
        server.enqueue(json(403, "{\"message\":\"Sorry, you are not allowed to upload files.\"}"));

        IOException e = assertThrows(IOException.class,
                () -> service.uploadMedia(siteUrl, USER, PASSWORD, imageUrl(), "t"));
        assertTrue(e.getMessage().contains("403"), e.getMessage());
    }

    @Test
    void uploadMedia_rejectsAnInvalidImageUrl() {
        assertThrows(IOException.class, () -> service.uploadMedia(siteUrl, USER, PASSWORD, "not a url", "t"));
        assertThrows(IOException.class, () -> service.uploadMedia(siteUrl, USER, PASSWORD, null, "t"));
    }

    @Test
    void publishPost_setsTheFeaturedMedia() throws Exception {
        server.enqueue(json(201, "{\"id\":5,\"link\":\"https://example.com/p\"}"));

        service.publishPost(siteUrl, USER, PASSWORD, "T", "<p>b</p>", null, 7, 88);

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"featured_media\":88"), body);
        assertTrue(body.contains("\"categories\":[7]"), body);
    }

    @Test
    void publishPost_omitsFeaturedMediaWhenThereIsNone() throws Exception {
        server.enqueue(json(201, "{\"id\":5,\"link\":\"https://example.com/p\"}"));

        service.publishPost(siteUrl, USER, PASSWORD, "T", "<p>b</p>", null, null, null);

        assertFalse(server.takeRequest().getBody().readUtf8().contains("featured_media"));
    }

    @Test
    void publishPost_asDraft_sendsDraftStatusAndReturnsTheEditorLink() throws Exception {
        server.enqueue(json(201, "{\"id\":42,\"link\":\"https://example.com/?p=42\"}"));

        String url = service.publishPost(siteUrl, USER, PASSWORD, "t", "<p>x</p>", null, null, null, true);

        assertEquals(siteUrl + "/wp-admin/post.php?post=42&action=edit", url);
        assertTrue(server.takeRequest().getBody().readUtf8().contains("\"status\":\"draft\""));
    }

    @Test
    void publishPost_published_returnsThePublicLink() throws Exception {
        server.enqueue(json(201, "{\"id\":42,\"link\":\"https://example.com/hello/\"}"));

        String url = service.publishPost(siteUrl, USER, PASSWORD, "t", "<p>x</p>", null, null, null, false);

        assertEquals("https://example.com/hello/", url);
        assertTrue(server.takeRequest().getBody().readUtf8().contains("\"status\":\"publish\""));
    }

    @Test
    void createPost_sendsTheExcerptAndTags() throws Exception {
        server.enqueue(json(201, "{\"id\":7,\"link\":\"https://example.com/p/\"}"));

        service.createPost(siteUrl, USER, PASSWORD, "t", "<p>x</p>", null,
                new WordPressService.PostOptions(null, null, false, "وصف قصير", List.of(4, 9)));

        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"excerpt\":\"وصف قصير\""), body);
        assertTrue(body.contains("\"tags\":[4,9]"), body);
    }

    @Test
    void createPost_leavesOutEmptyOptionalFields() throws Exception {
        server.enqueue(json(201, "{\"id\":7,\"link\":\"https://example.com/p/\"}"));

        service.createPost(siteUrl, USER, PASSWORD, "t", "<p>x</p>", null,
                new WordPressService.PostOptions(null, null, false, "  ", null));

        String body = server.takeRequest().getBody().readUtf8();
        assertFalse(body.contains("excerpt"), body);
        assertFalse(body.contains("tags"), body);
    }

    @Test
    void findOrCreateTagId_usesTheTagsEndpoint() throws Exception {
        server.enqueue(json(200, "[]"));
        server.enqueue(json(201, "{\"id\":33,\"name\":\"شاي\"}"));

        assertEquals(33, service.findOrCreateTagId(siteUrl, USER, PASSWORD, "شاي"));
        assertTrue(server.takeRequest().getPath().startsWith("/wp-json/wp/v2/tags?"));
        RecordedRequest create = server.takeRequest();
        assertEquals("POST", create.getMethod());
        assertEquals("/wp-json/wp/v2/tags", create.getPath());
    }

    @Test
    void setMediaAltText_updatesTheMediaItem() throws Exception {
        server.enqueue(json(200, "{\"id\":88}"));

        service.setMediaAltText(siteUrl, USER, PASSWORD, 88, "كوب شاي");

        RecordedRequest request = server.takeRequest();
        assertEquals("/wp-json/wp/v2/media/88", request.getPath());
        assertTrue(request.getBody().readUtf8().contains("\"alt_text\":\"كوب شاي\""));
    }

    @Test
    void setMediaAltText_reportsAFailure() {
        server.enqueue(json(403, "{\"message\":\"forbidden\"}"));

        assertThrows(IOException.class, () -> service.setMediaAltText(siteUrl, USER, PASSWORD, 88, "x"));
    }

    @Test
    void uploadMedia_acceptsAGeneratedImageWithoutDownloadingAnything() throws Exception {
        server.enqueue(json(201, "{\"id\":91}"));
        String dataUri = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(IMAGE_BYTES);

        assertEquals(91, service.uploadMedia(siteUrl, USER, PASSWORD, dataUri, "t"));

        RecordedRequest upload = server.takeRequest();
        assertEquals("/wp-json/wp/v2/media", upload.getPath(), "the only request is the upload");
        assertEquals("image/jpeg", upload.getHeader("Content-Type"));
        assertTrue(upload.getHeader("Content-Disposition").contains(".jpg"));
        assertArrayEquals(IMAGE_BYTES, upload.getBody().readByteArray());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void decodeDataUri_rejectsMalformedData() {
        assertThrows(IOException.class, () -> WordPressService.decodeDataUri("data:image/png,notbase64"));
        assertThrows(IOException.class, () -> WordPressService.decodeDataUri("data:image/png;base64,%%%"));
        assertThrows(IOException.class, () -> WordPressService.decodeDataUri("data:image/png;base64,"));
    }
}
