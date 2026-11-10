package com.hamzatech.publisher.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hamzatech.publisher.service.ConfigService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * المشاركة في قناة تيليجرام، مفحوصة ضد خادم حقيقي يقلّد Bot API.
 */
class TelegramSharerTest {

    private static final String TOKEN = "123456:SECRET-token";
    private static final String URL = "https://blog.example.com/2026/10/post.html";

    private final ConfigService configService = ConfigService.getInstance();
    private MockWebServer server;
    private TelegramSharer sharer;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        sharer = new TelegramSharer(server.url("/").toString());
        configService.set(ConfigService.TELEGRAM_BOT_TOKEN, TOKEN);
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
        configService.set(ConfigService.TELEGRAM_BOT_TOKEN, null);
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json").setBody(body);
    }

    private static JsonObject body(RecordedRequest request) {
        return JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
    }

    @Test
    void text_putsTheTitleInBoldThenTheDescriptionThenTheLink() {
        String text = TelegramSharer.text(new SocialSharer.SharedPost("Title", "Description", URL, null), 1024);

        assertEquals("<b>Title</b>\n\nDescription\n\n" + URL, text);
    }

    @Test
    void text_escapesHtmlSoATitleCannotBreakTheMarkup() {
        String text = TelegramSharer.text(new SocialSharer.SharedPost("A <b> & C", null, URL, null), 1024);

        assertEquals("<b>A &lt;b&gt; &amp; C</b>\n\n" + URL, text);
    }

    @Test
    void text_shortensTheDescriptionToFitTheCaptionLimit_butNeverTheLink() {
        String longDescription = "word ".repeat(400);

        String text = TelegramSharer.text(new SocialSharer.SharedPost("Title", longDescription, URL, null),
                TelegramSharer.CAPTION_LIMIT);

        String visible = text.replace("<b>", "").replace("</b>", "");
        assertTrue(visible.length() <= TelegramSharer.CAPTION_LIMIT, "visible length " + visible.length());
        assertTrue(text.endsWith(URL), text);
        assertTrue(text.contains("…"), "a cut description is marked as cut");
    }

    @Test
    void normalizeChat_acceptsTheFormsUsersType() {
        assertEquals("@mychannel", TelegramSharer.normalizeChat("@mychannel"));
        assertEquals("@mychannel", TelegramSharer.normalizeChat("mychannel"));
        assertEquals("@mychannel", TelegramSharer.normalizeChat("https://t.me/mychannel/"));
        assertEquals("-1001234567890", TelegramSharer.normalizeChat(" -1001234567890 "));
        assertNull(TelegramSharer.normalizeChat("  "));
    }

    @Test
    void share_withAHostedImage_sendsAPhotoWithTheCaption() throws Exception {
        server.enqueue(json(200, "{\"ok\":true,\"result\":{\"message_id\":42,"
                + "\"chat\":{\"id\":-1001,\"username\":\"mychannel\",\"type\":\"channel\"}}}"));

        String link = sharer.share(new SocialSharer.SharedPost("Title", "Desc", URL,
                "https://images.example.com/a.jpg"), "@mychannel");

        assertEquals("https://t.me/mychannel/42", link);
        RecordedRequest request = server.takeRequest();
        assertEquals("/bot" + TOKEN + "/sendPhoto", request.getPath());
        JsonObject sent = body(request);
        assertEquals("@mychannel", sent.get("chat_id").getAsString());
        assertEquals("https://images.example.com/a.jpg", sent.get("photo").getAsString());
        assertEquals("HTML", sent.get("parse_mode").getAsString());
        assertTrue(sent.get("caption").getAsString().contains(URL));
    }

    @Test
    void share_withoutAnImage_sendsATextMessageWithALinkPreview() throws Exception {
        server.enqueue(json(200, "{\"ok\":true,\"result\":{\"message_id\":7,"
                + "\"chat\":{\"id\":-1009876,\"type\":\"channel\"}}}"));

        String link = sharer.share(new SocialSharer.SharedPost("Title", null, URL, null), "-1009876");

        // قناة خاصة بلا اسم مستخدم: رابط t.me/c/ بالمعرّف بلا بادئة -100
        assertEquals("https://t.me/c/9876/7", link);
        RecordedRequest request = server.takeRequest();
        assertEquals("/bot" + TOKEN + "/sendMessage", request.getPath());
        JsonObject sent = body(request);
        assertEquals(URL, sent.getAsJsonObject("link_preview_options").get("url").getAsString());
    }

    @Test
    void share_whenTheImageIsRejected_fallsBackToATextMessage() throws Exception {
        server.enqueue(json(400, "{\"ok\":false,\"error_code\":400,"
                + "\"description\":\"Bad Request: wrong file identifier/HTTP URL specified\"}"));
        server.enqueue(json(200, "{\"ok\":true,\"result\":{\"message_id\":8,"
                + "\"chat\":{\"id\":-1001,\"username\":\"mychannel\"}}}"));

        String link = sharer.share(new SocialSharer.SharedPost("Title", "Desc", URL,
                "https://images.example.com/broken.jpg"), "@mychannel");

        assertEquals("https://t.me/mychannel/8", link);
        assertTrue(server.takeRequest().getPath().endsWith("/sendPhoto"));
        assertTrue(server.takeRequest().getPath().endsWith("/sendMessage"));
    }

    @Test
    void share_failure_reportsTelegramsReason_withoutLeakingTheToken() {
        server.enqueue(json(403, "{\"ok\":false,\"error_code\":403,"
                + "\"description\":\"Forbidden: bot is not a member of the channel chat\"}"));

        IOException e = assertThrows(IOException.class, () -> sharer.share(
                new SocialSharer.SharedPost("Title", null, URL, null), "@mychannel"));

        assertTrue(e.getMessage().contains("bot is not a member"), e.getMessage());
        assertFalse(e.getMessage().contains(TOKEN), "the bot token must never reach the publish log");
    }

    @Test
    void redact_removesTheTokenFromARetryInterceptorMessage() {
        // RetryInterceptor يضع الرابط كاملًا في رسالته، والتوكن جزء من المسار
        String message = "Request failed after 3 attempts: https://api.telegram.org/bot" + TOKEN + "/sendMessage";

        String redacted = TelegramSharer.redact(message, TOKEN);

        assertFalse(redacted.contains(TOKEN), redacted);
    }

    @Test
    void share_withNoToken_failsClearly() {
        assumeTrue(System.getenv("TELEGRAM_BOT_TOKEN") == null, "a token in the environment counts as configured");
        configService.set(ConfigService.TELEGRAM_BOT_TOKEN, null);

        assertFalse(sharer.isConfigured());
        assertThrows(IllegalStateException.class, () -> sharer.share(
                new SocialSharer.SharedPost("Title", null, URL, null), "@mychannel"));
    }
}
