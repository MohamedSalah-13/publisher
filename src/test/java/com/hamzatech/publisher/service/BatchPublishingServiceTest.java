package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.BatchPublishResult;
import com.hamzatech.publisher.model.BatchStatus;
import com.hamzatech.publisher.model.PublishJob;
import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.model.QueueItemStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchPublishingServiceTest {

    private static final PublishTarget TARGET =
            PublishTarget.ofBlogger("blog-1", "My Blog", "https://example.com");

    @AfterEach
    void clearQueue() {
        PublishQueueService.getInstance().clearAllJobs();
    }

    /** ينشر بلا شبكة: كل عنوان يبدأ بالبادئة المعطاة يفشل. */
    private static BatchPublishingService serviceFailing(String failingPrefix) {
        return new BatchPublishingService((title, target, category, onStage) ->
                title.startsWith(failingPrefix)
                        ? PublishLog.failure(title, target.getName(), target.getId(), category, "boom: " + title)
                        : PublishLog.success(title, target.getName(), target.getId(),
                        "id-" + title, "url-" + title, category));
    }

    private static BatchPublishingService serviceAlwaysOk() {
        return serviceFailing("never-matches-");
    }

    @Test
    void allSucceed_reportsSuccess() {
        BatchPublishResult result = serviceAlwaysOk()
                .publishAll(List.of("a", "b", "c"), TARGET, "cat", 0, null, () -> false);

        assertEquals(BatchStatus.SUCCESS, result.status());
        assertEquals(3, result.succeeded().size());
        assertTrue(result.failed().isEmpty());
        assertEquals(0, result.notAttempted());
    }

    @Test
    void oneArticleFailing_doesNotReportTheBatchAsSuccessful() {
        // الخطأ الأصلي: الحلقة كانت تُهمل نتيجة كل مقال فتظهر الدفعة ناجحة دائمًا
        BatchPublishResult result = serviceFailing("bad")
                .publishAll(List.of("good-1", "bad-2", "good-3"), TARGET, "cat", 0, null, () -> false);

        assertEquals(BatchStatus.PARTIAL_SUCCESS, result.status());
        assertEquals(2, result.succeeded().size());
        assertEquals(1, result.failed().size());
        assertEquals(List.of("bad-2"), result.failedTitles());
        assertEquals("boom: bad-2", result.failed().get(0).getErrorMessage());
    }

    @Test
    void oneArticleFailing_doesNotStopTheRest() {
        BatchPublishResult result = serviceFailing("bad")
                .publishAll(List.of("bad-1", "good-2", "good-3"), TARGET, "cat", 0, null, () -> false);

        assertEquals(3, result.succeeded().size() + result.failed().size(),
                "a failure must not abort the batch");
    }

    @Test
    void everyArticleFailing_reportsFailedNotPartial() {
        BatchPublishResult result = serviceFailing("bad")
                .publishAll(List.of("bad-1", "bad-2"), TARGET, "cat", 0, null, () -> false);

        assertEquals(BatchStatus.FAILED, result.status());
        assertTrue(result.succeeded().isEmpty());
    }

    @Test
    void emptyFile_reportsNothingToPublish() {
        assertEquals(BatchStatus.NOTHING_TO_PUBLISH,
                serviceAlwaysOk().publishAll(List.of(), TARGET, "cat", 0, null, () -> false).status());
        assertEquals(BatchStatus.NOTHING_TO_PUBLISH,
                serviceAlwaysOk().publishAll(null, TARGET, "cat", 0, null, () -> false).status());
    }

    @Test
    void cancelling_stopsTheBatchAndCountsWhatWasNotAttempted() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger published = new AtomicInteger();

        BatchPublishingService service = new BatchPublishingService((title, target, category, onStage) -> {
            if (published.incrementAndGet() == 2) {
                cancelled.set(true);
            }
            return PublishLog.success(title, target.getName(), target.getId(), "id", "url", category);
        });

        BatchPublishResult result = service.publishAll(
                List.of("a", "b", "c", "d", "e"), TARGET, "cat", 0, null, cancelled::get);

        assertEquals(BatchStatus.CANCELLED, result.status());
        assertEquals(2, result.succeeded().size());
        assertEquals(3, result.notAttempted());
    }

    @Test
    void noWaitAfterTheLastArticle() {
        // كانت الحلقة تنتظر بعد آخر مقال ثم تنهي الدفعة بلا سبب
        List<Integer> waits = new ArrayList<>();

        serviceAlwaysOk().publishAll(List.of("a", "b", "c"), TARGET, "cat", 2, recordingListener(waits), () -> false);

        // فاصلان بين ثلاثة مقالات، كل فاصل ثانيتان => 4 نداءات، ولا شيء بعد الثالث
        assertEquals(4, waits.size(), "expected waits only between articles");
    }

    @Test
    void singleArticle_neverWaits() {
        List<Integer> waits = new ArrayList<>();
        serviceAlwaysOk().publishAll(List.of("only"), TARGET, "cat", 5, recordingListener(waits), () -> false);
        assertTrue(waits.isEmpty(), "a one-article batch must not wait at all");
    }

    @Test
    void cancellingDuringTheWait_stopsBeforeTheNextArticle() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        BatchPublishingService.ProgressListener listener = new BatchPublishingService.ProgressListener() {
            public void onArticleStarted(int n, int total, String title) {
            }

            public void onStage(int n, int total, ArticlePublishingService.Stage stage) {
            }

            public void onArticleFinished(int n, int total, PublishLog result) {
            }

            public void onArticleSkipped(int n, int total, String title) {
            }

            public void onWaiting(int secondsRemaining) {
                cancelled.set(true);
            }
        };

        BatchPublishResult result = serviceAlwaysOk()
                .publishAll(List.of("a", "b", "c"), TARGET, "cat", 5, listener, cancelled::get);

        assertEquals(BatchStatus.CANCELLED, result.status());
        assertEquals(1, result.succeeded().size());
        assertEquals(2, result.notAttempted());
    }

    @Test
    void progressListener_seesEveryArticleOnceInOrder() {
        List<String> started = new ArrayList<>();
        List<Integer> finished = new ArrayList<>();

        BatchPublishingService.ProgressListener listener = new BatchPublishingService.ProgressListener() {
            public void onArticleStarted(int n, int total, String title) {
                started.add(n + "/" + total + ":" + title);
            }

            public void onStage(int n, int total, ArticlePublishingService.Stage stage) {
            }

            public void onArticleFinished(int n, int total, PublishLog result) {
                finished.add(n);
            }

            public void onArticleSkipped(int n, int total, String title) {
            }

            public void onWaiting(int secondsRemaining) {
            }
        };

        serviceFailing("bad").publishAll(List.of("a", "bad-b"), TARGET, "cat", 0, listener, () -> false);

        assertEquals(List.of("1/2:a", "2/2:bad-b"), started);
        assertEquals(List.of(1, 2), finished, "a failed article must still report completion");
    }

    @Test
    void notAttempted_isZeroWhenTheBatchRunsToTheEnd() {
        BatchPublishResult result = serviceFailing("bad")
                .publishAll(List.of("bad-1", "good-2"), TARGET, "cat", 0, null, () -> false);

        assertEquals(0, result.notAttempted());
        assertFalse(result.cancelled());
    }

    @Test
    void alreadyPublishedTitlesAreSkipped_notRepublished() {
        List<String> attempted = new ArrayList<>();
        BatchPublishingService service = new BatchPublishingService(
                (title, target, category, onStage) -> {
                    attempted.add(title);
                    return PublishLog.success(title, target.getName(), target.getId(), "id", "url", category);
                },
                blocking("old-1"));

        BatchPublishResult result = service.publishAll(
                List.of("old-1", "new-2"), TARGET, "cat", 0, null, () -> false);

        assertEquals(List.of("new-2"), attempted, "the duplicate must never reach the publisher");
        assertEquals(List.of("old-1"), result.skipped());
        assertEquals(1, result.succeeded().size());
        assertEquals(BatchStatus.SUCCESS, result.status());
        assertEquals(0, result.notAttempted(), "a skip is accounted for, not left dangling");
    }

    @Test
    void aBatchOfNothingButDuplicatesIsNotReportedAsSuccess() {
        BatchPublishingService service = new BatchPublishingService(
                (title, target, category, onStage) -> {
                    throw new AssertionError("must not publish anything");
                },
                blocking("a", "b"));

        BatchPublishResult result = service.publishAll(
                List.of("a", "b"), TARGET, "cat", 0, null, () -> false);

        assertEquals(BatchStatus.ALL_SKIPPED, result.status());
        assertEquals(2, result.skipped().size());
        assertTrue(result.succeeded().isEmpty());
    }

    @Test
    void aDuplicateWithinTheSameFileIsPublishedOnce() {
        List<String> attempted = new ArrayList<>();
        BatchPublishingService service = new BatchPublishingService(
                (title, target, category, onStage) -> {
                    attempted.add(title);
                    return PublishLog.success(title, target.getName(), target.getId(), "id", "url", category);
                },
                DuplicateGuard.fromPublishLog());

        BatchPublishResult result = service.publishAll(
                List.of("same", "same"), TARGET, "cat", 0, null, () -> false);

        assertEquals(List.of("same"), attempted);
        assertEquals(1, result.succeeded().size());
        assertEquals(List.of("same"), result.skipped());
    }

    @Test
    void skippingReportsProgressWithoutClaimingTheArticleWasWritten() {
        List<String> started = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        BatchPublishingService.ProgressListener listener = new BatchPublishingService.ProgressListener() {
            public void onArticleStarted(int n, int total, String title) {
                started.add(title);
            }

            public void onStage(int n, int total, ArticlePublishingService.Stage stage) {
            }

            public void onArticleFinished(int n, int total, PublishLog result) {
            }

            public void onArticleSkipped(int n, int total, String title) {
                skipped.add(title);
            }

            public void onWaiting(int secondsRemaining) {
            }
        };

        serviceAlwaysOk(blocking("old"))
                .publishAll(List.of("old", "new"), TARGET, "cat", 0, listener, () -> false);

        assertEquals(List.of("new"), started, "a skipped article was never started");
        assertEquals(List.of("old"), skipped);
    }

    @Test
    void skippingCostsNoDelay() {
        // لم يحدث نداء شبكة، فلا معنى لتهدئة المعدل بعده
        List<Integer> waits = new ArrayList<>();

        serviceAlwaysOk(blocking("a", "b"))
                .publishAll(List.of("a", "b", "c"), TARGET, "cat", 5, recordingListener(waits), () -> false);

        assertTrue(waits.isEmpty(), "only one article ran, so nothing to wait between: " + waits);
    }

    /** حارس يمنع العناوين المذكورة ويسمح بما عداها. */
    private static DuplicateGuard blocking(String... blockedTitles) {
        java.util.Set<String> blocked = new java.util.HashSet<>(List.of(blockedTitles));
        return new DuplicateGuard() {
            public boolean alreadyPublished(String title, PublishTarget target) {
                return blocked.contains(title);
            }

            public void recordPublished(String title, PublishTarget target) {
                blocked.add(title);
            }
        };
    }

    private static BatchPublishingService serviceAlwaysOk(DuplicateGuard guard) {
        return new BatchPublishingService(
                (title, target, category, onStage) ->
                        PublishLog.success(title, target.getName(), target.getId(), "id", "url", category),
                guard);
    }

    @Test
    void publishAll_removesTheJobFromTheQueueWhenItCompletes() {
        serviceAlwaysOk().publishAll(List.of("a", "b"), TARGET, "cat", 0, null, () -> false);

        boolean stillQueued = PublishQueueService.getInstance().getResumableJobs().stream()
                .anyMatch(j -> j.getTargetId().equals(TARGET.getId()));
        assertFalse(stillQueued, "a completed batch has nothing left to resume");
    }

    @Test
    void cancelling_leavesAResumableJobBehind() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        BatchPublishingService service = new BatchPublishingService((title, target, category, onStage) -> {
            cancelled.set(true); // أوقف بعد أول مقال
            return PublishLog.success(title, target.getName(), target.getId(), "id", "url", category);
        });

        service.publishAll(List.of("a", "b", "c"), TARGET, "cat", 0, null, cancelled::get);

        PublishJob job = PublishQueueService.getInstance().getResumableJobs().stream()
                .filter(j -> j.getTargetId().equals(TARGET.getId()))
                .findFirst().orElse(null);
        assertTrue(job != null && job.hasPending(), "the two unattempted titles must remain resumable");
        assertEquals(QueueItemStatus.SUCCEEDED, job.getItems().get(0).getStatus());
        assertEquals(QueueItemStatus.PENDING, job.getItems().get(1).getStatus());
        assertEquals(QueueItemStatus.PENDING, job.getItems().get(2).getStatus());
    }

    @Test
    void resumeJob_doesNotRepublishAlreadyResolvedItems() {
        List<String> attempted = new ArrayList<>();
        BatchPublishingService service = new BatchPublishingService((title, target, category, onStage) -> {
            attempted.add(title);
            return PublishLog.success(title, target.getName(), target.getId(), "id", "url", category);
        });

        PublishJob job = PublishQueueService.getInstance().createJob(TARGET, "cat", List.of("a", "b", "c"));
        PublishQueueService.getInstance().updateItem(job.getId(), 0, QueueItemStatus.SUCCEEDED, null);
        PublishQueueService.getInstance().updateItem(job.getId(), 1, QueueItemStatus.FAILED, "boom");
        job = PublishQueueService.getInstance().getJob(job.getId());

        BatchPublishResult result = service.resumeJob(job, TARGET, 0, null, () -> false);

        assertEquals(List.of("c"), attempted, "only the still-pending title is published");
        assertEquals(2, result.succeeded().size() + result.failed().size() - 1,
                "the two resolved items are tallied without a fresh network call: 1 old success + 1 old failure");
        assertEquals(BatchStatus.PARTIAL_SUCCESS, result.status());
    }

    @Test
    void resumeJob_completingTheLastPendingItemClearsTheQueue() {
        BatchPublishingService service = serviceAlwaysOk();

        PublishJob job = PublishQueueService.getInstance().createJob(TARGET, "cat", List.of("a", "b"));
        PublishQueueService.getInstance().updateItem(job.getId(), 0, QueueItemStatus.SUCCEEDED, null);
        job = PublishQueueService.getInstance().getJob(job.getId());

        service.resumeJob(job, TARGET, 0, null, () -> false);

        assertNull(PublishQueueService.getInstance().getJob(job.getId()));
    }

    private static BatchPublishingService.ProgressListener recordingListener(List<Integer> waits) {
        return new BatchPublishingService.ProgressListener() {
            public void onArticleStarted(int n, int total, String title) {
            }

            public void onStage(int n, int total, ArticlePublishingService.Stage stage) {
            }

            public void onArticleFinished(int n, int total, PublishLog result) {
            }

            public void onArticleSkipped(int n, int total, String title) {
            }

            public void onWaiting(int secondsRemaining) {
                waits.add(secondsRemaining);
            }
        };
    }
}
