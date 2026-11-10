package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.PublishJob;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.model.QueueItemStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishQueueServiceTest {

    private static final PublishTarget TARGET =
            PublishTarget.ofBlogger("queue-test-blog", "Queue Test Blog", "https://example.com");

    private final PublishQueueService queueService = PublishQueueService.getInstance();

    @AfterEach
    void clearQueue() {
        queueService.clearAllJobs();
    }

    @Test
    void pendingCountExcludesResolvedItemsAcrossJobs() {
        queueService.clearAllJobs();
        PublishJob first = queueService.createJob(TARGET, "cat", List.of("a", "b"));
        PublishJob second = queueService.createJob(TARGET, "cat", List.of("c", "d"));
        queueService.updateItem(first.getId(), 0, QueueItemStatus.SUCCEEDED, null);
        queueService.updateItem(second.getId(), 0, QueueItemStatus.FAILED, "network");
        assertEquals(2, queueService.getPendingCount());
    }

    @Test
    void createJob_startsEveryItemPending() {
        PublishJob job = queueService.createJob(TARGET, "cat", List.of("a", "b", "c"));

        assertEquals(3, job.total());
        assertTrue(job.getItems().stream().allMatch(i -> i.getStatus() == QueueItemStatus.PENDING));
        assertTrue(job.hasPending());
    }

    @Test
    void createJob_isImmediatelyPersisted() {
        PublishJob job = queueService.createJob(TARGET, "cat", List.of("a"));

        assertEquals(job.getId(), queueService.getJob(job.getId()).getId());
    }

    @Test
    void aFreshJobIsResumable() {
        PublishJob job = queueService.createJob(TARGET, "cat", List.of("a"));

        assertTrue(queueService.getResumableJobs().stream().anyMatch(j -> j.getId().equals(job.getId())));
    }

    @Test
    void updateItem_changesOnlyThatItem() {
        PublishJob job = queueService.createJob(TARGET, "cat", List.of("a", "b"));

        queueService.updateItem(job.getId(), 0, QueueItemStatus.SUCCEEDED, null);

        PublishJob reloaded = queueService.getJob(job.getId());
        assertEquals(QueueItemStatus.SUCCEEDED, reloaded.getItems().get(0).getStatus());
        assertEquals(QueueItemStatus.PENDING, reloaded.getItems().get(1).getStatus());
    }

    @Test
    void updateItem_toFailedKeepsTheErrorMessage() {
        PublishJob job = queueService.createJob(TARGET, "cat", List.of("a"));

        queueService.updateItem(job.getId(), 0, QueueItemStatus.FAILED, "network timeout");

        assertEquals("network timeout", queueService.getJob(job.getId()).getItems().get(0).getErrorMessage());
    }

    @Test
    void finish_removesAJobWithNothingLeftPending() {
        PublishJob job = queueService.createJob(TARGET, "cat", List.of("a"));
        queueService.updateItem(job.getId(), 0, QueueItemStatus.SUCCEEDED, null);

        queueService.finish(job.getId());

        assertNull(queueService.getJob(job.getId()));
    }

    @Test
    void finish_keepsAJobThatStillHasPendingItems() {
        // إيقاف مبكر (مستخدم أوقف الدفعة، أو أُغلق التطبيق) لا ينبغي أن يمحو
        // العمل المتبقي
        PublishJob job = queueService.createJob(TARGET, "cat", List.of("a", "b"));
        queueService.updateItem(job.getId(), 0, QueueItemStatus.SUCCEEDED, null);

        queueService.finish(job.getId());

        PublishJob reloaded = queueService.getJob(job.getId());
        assertTrue(reloaded != null && reloaded.hasPending());
    }

    @Test
    void deleteJob_removesItEvenWithPendingItems() {
        // "تجاهل" من شاشة الطابور: المستخدم لا يريد استئنافها
        PublishJob job = queueService.createJob(TARGET, "cat", List.of("a", "b"));

        queueService.deleteJob(job.getId());

        assertNull(queueService.getJob(job.getId()));
    }

    @Test
    void getResumableJobs_excludesJobsWithNothingPending() {
        PublishJob done = queueService.createJob(TARGET, "cat", List.of("a"));
        queueService.updateItem(done.getId(), 0, QueueItemStatus.SUCCEEDED, null);
        // لم يُستدعَ finish() بعد، فما زال في الملف تقنيًا، لكن getResumableJobs
        // يجب ألا يعرضه لأن لا شيء فيه يستحق الاستئناف
        PublishJob pending = queueService.createJob(TARGET, "cat", List.of("b"));

        List<String> resumableIds = queueService.getResumableJobs().stream().map(PublishJob::getId).toList();

        assertFalse(resumableIds.contains(done.getId()));
        assertTrue(resumableIds.contains(pending.getId()));
    }

    @Test
    void aTitleWithArabicTextRoundTripsIntact() {
        PublishJob job = queueService.createJob(TARGET, "طعام", List.of("الكسكس المغربي"));

        PublishJob reloaded = queueService.getJob(job.getId());

        assertEquals("الكسكس المغربي", reloaded.getItems().get(0).getTitle());
        assertEquals("طعام", reloaded.getCategory());
    }

    @Test
    void countByStatus_countsOnlyMatchingItems() {
        PublishJob job = queueService.createJob(TARGET, "cat", List.of("a", "b", "c"));
        queueService.updateItem(job.getId(), 0, QueueItemStatus.SUCCEEDED, null);
        queueService.updateItem(job.getId(), 1, QueueItemStatus.SUCCEEDED, null);

        PublishJob reloaded = queueService.getJob(job.getId());

        assertEquals(2, reloaded.countByStatus(QueueItemStatus.SUCCEEDED));
        assertEquals(1, reloaded.countByStatus(QueueItemStatus.PENDING));
        assertEquals(0, reloaded.countByStatus(QueueItemStatus.FAILED));
    }
}
