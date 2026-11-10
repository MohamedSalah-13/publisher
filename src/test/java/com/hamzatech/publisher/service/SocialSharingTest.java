package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.ArticleContent;
import com.hamzatech.publisher.model.EffectiveSettings;
import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.model.SeoMetadata;
import com.hamzatech.publisher.provider.ProviderRegistry;
import com.hamzatech.publisher.provider.SocialSharer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * المشاركة بعد النشر: متى تحدث، وأن فشلها لا يمسّ المقال المنشور.
 */
class SocialSharingTest {

    private static final String URL = "https://blog.example.com/p/1.html";

    /** الإعدادات العامة بلا قنوات: المزودان المدمجان لا يشاركان، فلا يبقى إلا المزيَّفون. */
    private static final EffectiveSettings SETTINGS = new EffectiveSettings(
            "openai", "unsplash", "arabic", "arabic", "auto", 5, null, "publish", null, null);

    private static final ArticleContent ARTICLE = new ArticleContent("Original", "Published title",
            "<div><p>Body</p></div>", "https://images.example.com/a.jpg", null,
            new SeoMetadata("Meta description", List.of(), null));

    private final PublishLogService logService = PublishLogService.getInstance();

    /** شبكة مزيَّفة تسجّل ما طُلب منها، أو ترمي ما أُعطي لها. */
    private static final class FakeSharer implements SocialSharer {
        private final String id;
        private final boolean configured;
        private final Exception failure;
        final List<SharedPost> shared = new ArrayList<>();

        FakeSharer(String id, boolean configured, Exception failure) {
            this.id = id;
            this.configured = configured;
            this.failure = failure;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return "Fake-" + id;
        }

        public boolean isConfigured() {
            return configured;
        }

        public String destination(EffectiveSettings settings) {
            return "@" + id;
        }

        public String share(SharedPost post, String destination) throws IOException {
            if (failure instanceof IOException io) {
                throw io;
            }
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            shared.add(post);
            return "https://social.example.com/" + id;
        }
    }

    @BeforeEach
    @AfterEach
    void reset() {
        ProviderRegistry.resetToBuiltIns();
        logService.clearAllLogs();
    }

    @Test
    void aPublishedArticle_isSharedWithItsTitleDescriptionLinkAndImage() {
        FakeSharer sharer = new FakeSharer("a", true, null);
        ProviderRegistry.register(sharer);

        SocialSharing.Outcome outcome = SocialSharing.shareAll(ARTICLE, URL, false, SETTINGS);

        assertEquals(List.of("Fake-a"), outcome.sharedTo());
        assertNull(outcome.error());
        SocialSharer.SharedPost post = sharer.shared.get(0);
        assertEquals("Published title", post.title());
        assertEquals("Meta description", post.description());
        assertEquals(URL, post.url());
        assertEquals("https://images.example.com/a.jpg", post.imageUrl());
    }

    @Test
    void aDraft_isNeverShared_becauseItsLinkIsTheEditor() {
        FakeSharer sharer = new FakeSharer("a", true, null);
        ProviderRegistry.register(sharer);

        SocialSharing.Outcome outcome = SocialSharing.shareAll(ARTICLE,
                "https://site.example.com/wp-admin/post.php?post=5&action=edit", true, SETTINGS);

        assertTrue(sharer.shared.isEmpty());
        assertFalse(outcome.attempted());
    }

    @Test
    void anArticleWithNoPublicLink_isNotShared() {
        FakeSharer sharer = new FakeSharer("a", true, null);
        ProviderRegistry.register(sharer);

        assertFalse(SocialSharing.shareAll(ARTICLE, null, false, SETTINGS).attempted());
        assertFalse(SocialSharing.shareAll(ARTICLE, "  ", false, SETTINGS).attempted());
        assertTrue(sharer.shared.isEmpty());
    }

    @Test
    void aTargetWithNoChannels_sharesNowhere() {
        // المزودان المدمجان وحدهما، والإعدادات بلا قناة تيليجرام ولا صفحة فيسبوك
        assertFalse(SocialSharing.shareAll(ARTICLE, URL, false, SETTINGS).attempted());
    }

    @Test
    void aDataUriImage_isNotSent_becauseNoNetworkCanFetchIt() {
        ArticleContent withDataImage = new ArticleContent("T", "T", "<div/>", "data:image/png;base64,AAAA", null);

        SocialSharer.SharedPost post = SocialSharing.postFor(withDataImage, URL);

        assertNull(post.imageUrl());
        assertNull(post.description());
    }

    @Test
    void oneNetworkFailing_doesNotStopTheOthers() {
        FakeSharer broken = new FakeSharer("broken", true, new IOException("chat not found"));
        FakeSharer buggy = new FakeSharer("buggy", true, new IllegalArgumentException("bug"));
        FakeSharer working = new FakeSharer("ok", true, null);
        ProviderRegistry.register(broken);
        ProviderRegistry.register(buggy);
        ProviderRegistry.register(working);

        SocialSharing.Outcome outcome = SocialSharing.shareAll(ARTICLE, URL, false, SETTINGS);

        assertEquals(List.of("Fake-ok"), outcome.sharedTo());
        assertTrue(outcome.error().contains("Fake-broken: chat not found"), outcome.error());
        assertTrue(outcome.error().contains("Fake-buggy: bug"), outcome.error());
    }

    @Test
    void aNetworkWithNoToken_isSkippedWithAReason() {
        FakeSharer unconfigured = new FakeSharer("a", false, null);
        ProviderRegistry.register(unconfigured);

        SocialSharing.Outcome outcome = SocialSharing.shareAll(ARTICLE, URL, false, SETTINGS);

        assertTrue(unconfigured.shared.isEmpty());
        assertTrue(outcome.error().contains("token missing"), outcome.error());
    }

    @Test
    void aSharingFailure_leavesTheArticlePublished_andIsRecordedOnItsLog() {
        ProviderRegistry.register(new FakeSharer("broken", true, new IOException("bot is not an admin")));
        ProviderRegistry.register(new FakeSharer("ok", true, null));
        PublishLog published = PublishLog.success("Original", "Blog", "blog-1", "post-1", URL, null);
        logService.addLog(published);

        ArticlePublishingService.shareAfterPublish(published, ARTICLE, SETTINGS);

        assertTrue(published.isSuccess(), "a failed share must never turn a published article into a failure");
        PublishLog stored = logService.getAllLogs().get(0);
        assertTrue(stored.isSuccess());
        assertEquals(List.of("Fake-ok"), stored.getSharedTo());
        assertTrue(stored.getShareError().contains("bot is not an admin"), stored.getShareError());
        assertEquals(1, logService.getAllLogs().size(), "sharing updates the log, it does not add one");
    }

    @Test
    void shareAfterPublish_leavesADraftsLogUntouched() {
        FakeSharer sharer = new FakeSharer("a", true, null);
        ProviderRegistry.register(sharer);
        PublishLog draft = PublishLog.success("Original", "Blog", "blog-1", "post-2",
                "https://www.blogger.com/blog/post/edit/1/2", null);
        draft.setDraft(true);
        logService.addLog(draft);

        ArticlePublishingService.shareAfterPublish(draft, ARTICLE, SETTINGS);

        assertTrue(sharer.shared.isEmpty());
        assertNull(logService.getAllLogs().get(0).getSharedTo());
        assertNull(logService.getAllLogs().get(0).getShareError());
    }
}
