package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.service.ConfigService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * المشاركة على صفحة فيسبوك، مفحوصة ضد خادم حقيقي يقلّد Graph API.
 */
class FacebookPageSharerTest {

    private static final String CONFIGURED_TOKEN = "EAA-configured";
    private static final String URL = "https://blog.example.com/2026/10/post.html";
    private static final String FEED_PATH = "/" + FacebookPageSharer.GRAPH_VERSION + "/12345/feed";

    private final ConfigService configService = ConfigService.getInstance();
    private MockWebServer server;
    private FacebookPageSharer sharer;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        sharer = new FacebookPageSharer(server.url("/").toString());
        configService.set(ConfigService.FACEBOOK_PAGE_TOKEN, CONFIGURED_TOKEN);
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
        configService.set(ConfigService.FACEBOOK_PAGE_TOKEN, null);
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json").setBody(body);
    }

    private static String form(RecordedRequest request) {
        return URLDecoder.decode(request.getBody().readUtf8(), StandardCharsets.UTF_8);
    }

    @Test
    void message_isTheTitleThenTheDescription() {
        assertEquals("Title\n\nDesc", FacebookPageSharer.message(new SocialSharer.SharedPost("Title", "Desc", URL, null)));
        assertEquals("Title", FacebookPageSharer.message(new SocialSharer.SharedPost("Title", null, URL, null)));
    }

    @Test
    void normalizePageId_acceptsAnIdOrAPageLink() {
        assertEquals("12345", FacebookPageSharer.normalizePageId(" 12345 "));
        assertEquals("mypage", FacebookPageSharer.normalizePageId("https://www.facebook.com/mypage/"));
        assertEquals("mypage", FacebookPageSharer.normalizePageId("facebook.com/mypage?ref=bookmarks"));
        assertNull(FacebookPageSharer.normalizePageId(""));
    }

    @Test
    void share_withAUserToken_postsWithThePagesOwnToken() throws Exception {
        server.enqueue(json(200, "{\"access_token\":\"PAGE-token\",\"id\":\"12345\"}"));
        server.enqueue(json(200, "{\"id\":\"12345_678\"}"));

        String link = sharer.share(new SocialSharer.SharedPost("Title", "Desc", URL, null), "12345");

        assertEquals("https://www.facebook.com/12345_678", link);
        RecordedRequest lookup = server.takeRequest();
        assertEquals("GET", lookup.getMethod());
        assertTrue(lookup.getPath().startsWith("/" + FacebookPageSharer.GRAPH_VERSION + "/12345?"), lookup.getPath());

        RecordedRequest feed = server.takeRequest();
        assertEquals("POST", feed.getMethod());
        assertEquals(FEED_PATH, feed.getPath());
        String body = form(feed);
        assertTrue(body.contains("message=Title\n\nDesc"), body);
        assertTrue(body.contains("link=" + URL), body);
        assertTrue(body.contains("access_token=PAGE-token"), body);
    }

    @Test
    void share_withAPageToken_usesItAsIs_whenNoOtherTokenComesBack() throws Exception {
        server.enqueue(json(400, "{\"error\":{\"message\":\"(#100) Tried accessing nonexisting field\"}}"));
        server.enqueue(json(200, "{\"id\":\"12345_9\"}"));

        sharer.share(new SocialSharer.SharedPost("Title", null, URL, null), "12345");

        server.takeRequest();
        assertTrue(form(server.takeRequest()).contains("access_token=" + CONFIGURED_TOKEN));
    }

    @Test
    void share_remembersThePageToken_acrossArticles() throws Exception {
        server.enqueue(json(200, "{\"access_token\":\"PAGE-token\"}"));
        server.enqueue(json(200, "{\"id\":\"12345_1\"}"));
        server.enqueue(json(200, "{\"id\":\"12345_2\"}"));

        sharer.share(new SocialSharer.SharedPost("One", null, URL, null), "12345");
        sharer.share(new SocialSharer.SharedPost("Two", null, URL, null), "12345");

        assertEquals(3, server.getRequestCount(), "one lookup, then one post per article");
    }

    @Test
    void share_failure_reportsFacebooksReason() {
        server.enqueue(json(200, "{\"access_token\":\"PAGE-token\"}"));
        server.enqueue(json(403, "{\"error\":{\"message\":\"(#200) The user hasn't authorized the application"
                + " to perform this action\",\"code\":200}}"));

        IOException e = assertThrows(IOException.class, () -> sharer.share(
                new SocialSharer.SharedPost("Title", null, URL, null), "12345"));

        assertTrue(e.getMessage().startsWith("Facebook: (#200)"), e.getMessage());
    }
}
