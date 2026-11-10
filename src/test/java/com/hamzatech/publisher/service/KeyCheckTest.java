package com.hamzatech.publisher.service;

import com.hamzatech.publisher.service.KeyCheck.Outcome;
import com.hamzatech.publisher.service.KeyCheck.Result;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * فحص المفاتيح ضد خادم محلي: ما يُرسَل لكل مزود، وكيف يُقرأ رده.
 */
class KeyCheckTest {

    /** بلا RetryInterceptor: المقصود هنا قراءة الرد، وإعادة المحاولة مختبرة في مكانها. */
    private final OkHttpClient client = new OkHttpClient();
    private MockWebServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        baseUrl = server.url("").toString().replaceAll("/$", "");
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private void respond(int code) {
        server.enqueue(new MockResponse().setResponseCode(code).setBody("{}"));
    }

    @Test
    void openAi_sendsBearerKeyToModelsList() throws Exception {
        respond(200);

        Result result = KeyCheck.openAi("  sk-test  ", baseUrl, client);

        assertTrue(result.valid());
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/v1/models", request.getPath());
        assertEquals("Bearer sk-test", request.getHeader("Authorization"));
    }

    @Test
    void claude_sendsKeyAndVersionHeaders() throws Exception {
        respond(200);

        assertTrue(KeyCheck.claude("ant-key", baseUrl, client).valid());

        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/models", request.getPath());
        assertEquals("ant-key", request.getHeader("x-api-key"));
        assertEquals("2023-06-01", request.getHeader("anthropic-version"));
    }

    @Test
    void gemini_keepsTheKeyOutOfTheUrl() throws Exception {
        respond(200);

        assertTrue(KeyCheck.gemini("gem-key", baseUrl, client).valid());

        RecordedRequest request = server.takeRequest();
        assertEquals("gem-key", request.getHeader("x-goog-api-key"));
        // الرابط يصل إلى السجل عند الفشل، فلا يجوز أن يحمل المفتاح
        assertFalse(request.getPath().contains("gem-key"));
    }

    @Test
    void unsplash_sendsClientIdHeader() throws Exception {
        respond(200);

        assertTrue(KeyCheck.unsplash("access", baseUrl, client).valid());

        assertEquals("Client-ID access", server.takeRequest().getHeader("Authorization"));
    }

    @Test
    void unauthorizedMeansTheKeyWasRejected() {
        respond(401);

        Result result = KeyCheck.openAi("wrong", baseUrl, client);

        assertEquals(Outcome.REJECTED, result.outcome());
        assertEquals(401, result.httpStatus());
        assertFalse(result.inconclusive());
    }

    @Test
    void gemini_treatsBadRequestAsARejectedKey() {
        // Gemini يرد على مفتاح خاطئ بـ 400 API_KEY_INVALID
        respond(400);

        assertEquals(Outcome.REJECTED, KeyCheck.gemini("wrong", baseUrl, client).outcome());
    }

    @Test
    void badRequestFromAnotherProviderIsNotBlamedOnTheKey() {
        respond(400);

        Result result = KeyCheck.openAi("key", baseUrl, client);

        assertEquals(Outcome.FAILED, result.outcome());
        assertTrue(result.inconclusive());
    }

    @Test
    void serverErrorIsInconclusive() {
        respond(503);

        Result result = KeyCheck.unsplash("key", baseUrl, client);

        assertEquals(Outcome.FAILED, result.outcome());
        assertEquals(503, result.httpStatus());
        assertTrue(result.inconclusive());
    }

    @Test
    void noAnswerIsUnreachableNotRejected() throws IOException {
        server.shutdown();

        Result result = KeyCheck.claude("key", baseUrl, client);

        assertEquals(Outcome.UNREACHABLE, result.outcome());
        assertTrue(result.inconclusive());
    }

    @Test
    void keyThatCannotBeSentAsAHeaderIsRejectedWithoutARequest() {
        Result result = KeyCheck.openAi("sk-line1\nsk-line2", baseUrl, client);

        assertEquals(Outcome.REJECTED, result.outcome());
        assertEquals(0, server.getRequestCount());
    }
}
