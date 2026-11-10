package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.model.PublishTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateGuardTest {

    private static final PublishTarget BLOG_A =
            PublishTarget.ofBlogger("blog-a", "Blog A", "https://a.example.com");
    private static final PublishTarget BLOG_B =
            PublishTarget.ofBlogger("blog-b", "Blog B", "https://b.example.com");

    private final PublishLogService logs = PublishLogService.getInstance();

    @AfterEach
    void clearLogs() {
        logs.clearAllLogs();
    }

    @Test
    void allowAll_neverBlocksAnything() {
        assertFalse(DuplicateGuard.ALLOW_ALL.alreadyPublished("anything", BLOG_A));
        DuplicateGuard.ALLOW_ALL.recordPublished("anything", BLOG_A);
        assertFalse(DuplicateGuard.ALLOW_ALL.alreadyPublished("anything", BLOG_A));
    }

    @Test
    void aTitleAlreadyPublishedToTheSameBlogIsBlocked() {
        logs.addLog(PublishLog.success("Moroccan Couscous", "Blog A", "blog-a", "p1", "u1", "food"));

        DuplicateGuard guard = DuplicateGuard.fromPublishLog();

        assertTrue(guard.alreadyPublished("Moroccan Couscous", BLOG_A));
    }

    @Test
    void theSameTitleOnAnotherBlogIsAllowed() {
        // نشر المقال نفسه على مدونتين قصدٌ مشروع، لا تكرار
        logs.addLog(PublishLog.success("Moroccan Couscous", "Blog A", "blog-a", "p1", "u1", "food"));

        DuplicateGuard guard = DuplicateGuard.fromPublishLog();

        assertFalse(guard.alreadyPublished("Moroccan Couscous", BLOG_B));
    }

    @Test
    void aFailedAttemptDoesNotBlockARetry() {
        // وإلا صار زر "إعادة محاولة الفاشل" بلا فائدة
        logs.addLog(PublishLog.failure("Moroccan Couscous", "Blog A", "blog-a", "food", "boom"));

        DuplicateGuard guard = DuplicateGuard.fromPublishLog();

        assertFalse(guard.alreadyPublished("Moroccan Couscous", BLOG_A));
    }

    @Test
    void surroundingAndRepeatedWhitespaceIsIgnored() {
        // خلايا Excel تحمل مسافات زائدة كثيرًا
        logs.addLog(PublishLog.success("Moroccan Couscous", "Blog A", "blog-a", "p1", "u1", "food"));

        DuplicateGuard guard = DuplicateGuard.fromPublishLog();

        assertTrue(guard.alreadyPublished("  Moroccan Couscous  ", BLOG_A));
        assertTrue(guard.alreadyPublished("Moroccan   Couscous", BLOG_A));
    }

    @Test
    void caseIsIgnored() {
        logs.addLog(PublishLog.success("Moroccan Couscous", "Blog A", "blog-a", "p1", "u1", "food"));

        assertTrue(DuplicateGuard.fromPublishLog().alreadyPublished("moroccan couscous", BLOG_A));
    }

    @Test
    void arabicTitlesMatchToo() {
        logs.addLog(PublishLog.success("الكسكس المغربي", "Blog A", "blog-a", "p1", "u1", "طعام"));

        DuplicateGuard guard = DuplicateGuard.fromPublishLog();

        assertTrue(guard.alreadyPublished("  الكسكس المغربي ", BLOG_A));
        assertFalse(guard.alreadyPublished("الكسكس التونسي", BLOG_A));
    }

    @Test
    void aDifferentTitleIsNotBlocked() {
        logs.addLog(PublishLog.success("Moroccan Couscous", "Blog A", "blog-a", "p1", "u1", "food"));

        assertFalse(DuplicateGuard.fromPublishLog().alreadyPublished("Tunisian Couscous", BLOG_A));
    }

    @Test
    void recordingCatchesADuplicateWithinTheSameBatch() {
        // ملف Excel قد يحمل العنوان نفسه مرتين
        DuplicateGuard guard = DuplicateGuard.fromPublishLog();
        assertFalse(guard.alreadyPublished("New Article", BLOG_A));

        guard.recordPublished("New Article", BLOG_A);

        assertTrue(guard.alreadyPublished("New Article", BLOG_A));
        assertFalse(guard.alreadyPublished("New Article", BLOG_B));
    }

    @Test
    void anEmptyLogBlocksNothing() {
        assertFalse(DuplicateGuard.fromPublishLog().alreadyPublished("Anything", BLOG_A));
    }

    @Test
    void fingerprintCannotConfuseTwoDifferentPairs() {
        // بفاصل مسافة كان ("a", "b c") و("a b", "c") ينتجان البصمة نفسها،
        // فيُتخطّى مقال لم يُنشر قط
        assertNotEquals(DuplicateGuard.fingerprint("b c", "a"),
                DuplicateGuard.fingerprint("c", "a b"));
    }

    @Test
    void fingerprintCombinesTitleAndTarget() {
        assertEquals(DuplicateGuard.fingerprint("A Title", "blog-a"),
                DuplicateGuard.fingerprint(" a   title ", "blog-a"));
        assertNotEquals(DuplicateGuard.fingerprint("A Title", "blog-a"),
                DuplicateGuard.fingerprint("A Title", "blog-b"));
        assertNotEquals(DuplicateGuard.fingerprint(null, "blog-a"),
                DuplicateGuard.fingerprint("x", "blog-a"));
    }
}
