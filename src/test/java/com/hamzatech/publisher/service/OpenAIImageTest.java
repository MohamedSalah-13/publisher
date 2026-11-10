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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * توليد الصور عبر OpenAI، مفحوصًا ضد خادم محلي.
 */
class OpenAIImageTest {

    private MockWebServer server;
    private OpenAIService service;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        service = new OpenAIService("test-key", server.url("/v1").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json").setBody(body);
    }

    private JsonObject sentBody() throws InterruptedException {
        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/images/generations", request.getPath());
        assertEquals("Bearer test-key", request.getHeader("Authorization"));
        return JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
    }

    @Test
    void gptImage_returnsTheImageItself_asACompressedJpeg() throws Exception {
        server.enqueue(json(200, "{\"data\":[{\"b64_json\":\"QUJD\"}]}"));

        String image = service.generateImage("tea", "gpt-image-1", "medium");

        // بيانات لا رابط: روابط OpenAI تنتهي بعد ساعة
        assertEquals("data:image/jpeg;base64,QUJD", image);
        JsonObject body = sentBody();
        assertEquals("gpt-image-1", body.get("model").getAsString());
        assertEquals("jpeg", body.get("output_format").getAsString());
        assertEquals("1536x1024", body.get("size").getAsString());
        assertFalse(body.has("response_format"), "gpt-image models reject response_format");
    }

    @Test
    void gptImage_sendsTheChosenQuality() throws Exception {
        server.enqueue(json(200, "{\"data\":[{\"b64_json\":\"QUJD\"}]}"));

        service.generateImage("tea", "gpt-image-1", "low");

        assertEquals("low", sentBody().get("quality").getAsString());
    }

    @Test
    void dallE_asksForDataInsteadOfAnExpiringLink() throws Exception {
        server.enqueue(json(200, "{\"data\":[{\"b64_json\":\"QUJD\"}]}"));

        String image = service.generateImage("tea", "dall-e-3", "high");

        assertEquals("data:image/png;base64,QUJD", image);
        JsonObject body = sentBody();
        assertEquals("b64_json", body.get("response_format").getAsString());
        assertFalse(body.has("output_format"), "DALL-E rejects gpt-image parameters");
        assertFalse(body.has("quality"), "DALL-E does not accept low/medium/high");
    }

    @Test
    void aReplyWithOnlyALink_isAnError_notAnExpiringImage() {
        server.enqueue(json(200, "{\"data\":[{\"url\":\"https://example.com/x.png\"}]}"));

        assertThrows(IOException.class, () -> service.generateImage("tea", "gpt-image-1", "medium"));
    }

    @Test
    void anErrorResponse_namesTheModel() {
        server.enqueue(json(400, "{\"error\":{\"message\":\"bad model\"}}"));

        IOException e = assertThrows(IOException.class, () -> service.generateImage("tea", "nope", "medium"));
        assertTrue(e.getMessage().contains("nope"), e.getMessage());
    }
}
