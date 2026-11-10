package com.hamzatech.publisher.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * طلب Claude ورده، مفحوصين ضد خادم محلي.
 */
class ClaudeServiceTest {

    private MockWebServer server;
    private ClaudeService service;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        service = new ClaudeService("test-key", server.url("/v1/messages").toString());
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void generateArticle_sendsKeyVersionModelAndSystemPrompt() throws Exception {
        server.enqueue(json(200, "{\"content\":[{\"type\":\"text\",\"text\":\"<p>ok</p>\"}],\"stop_reason\":\"end_turn\"}"));

        String html = service.generateArticle("عنوان", "arabic", "claude-test-model");

        assertEquals("<p>ok</p>", html);
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("test-key", request.getHeader("x-api-key"));
        assertEquals("2023-06-01", request.getHeader("anthropic-version"));

        JsonObject body = JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
        assertEquals("claude-test-model", body.get("model").getAsString());
        assertTrue(body.get("max_tokens").getAsInt() > 0);
        assertTrue(body.get("system").getAsString().contains("HTML"));
        assertTrue(body.getAsJsonArray("messages").get(0).getAsJsonObject()
                .get("content").getAsString().contains("عنوان"));
    }

    @Test
    void generateArticle_cleansFencesFromTheReply() throws Exception {
        server.enqueue(json(200, "{\"content\":[{\"type\":\"text\",\"text\":\"```html\\n<p>x</p>\\n```\"}]}"));

        assertEquals("<p>x</p>", service.generateArticle("t", "english", "m"));
    }

    @Test
    void anErrorResponse_surfacesTheApiMessage() {
        server.enqueue(json(404, "{\"type\":\"error\",\"error\":{\"type\":\"not_found_error\",\"message\":\"model: nope\"}}"));

        IOException e = assertThrows(IOException.class, () -> service.generateArticle("t", "english", "nope"));
        assertTrue(e.getMessage().contains("model: nope"), e.getMessage());
        assertTrue(e.getMessage().contains("404"), e.getMessage());
    }

    @Test
    void aTruncatedArticle_isAFailureNotAPost() {
        String body = "{\"content\":[{\"type\":\"text\",\"text\":\"<p>half\"}],\"stop_reason\":\"max_tokens\"}";

        assertThrows(IOException.class, () -> ClaudeService.extractText(body));
    }

    @Test
    void textBlocks_areJoinedAndOtherBlocksIgnored() throws IOException {
        String body = "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"...\"},"
                + "{\"type\":\"text\",\"text\":\"<p>a</p>\"},{\"type\":\"text\",\"text\":\"<p>b</p>\"}]}";

        assertEquals("<p>a</p><p>b</p>", ClaudeService.extractText(body));
    }

    @Test
    void complete_returnsTrimmedText() throws Exception {
        server.enqueue(json(200, "{\"content\":[{\"type\":\"text\",\"text\":\"  Green tea  \"}]}"));

        assertEquals("Green tea", service.complete("translate", "m", 50));
    }
}
