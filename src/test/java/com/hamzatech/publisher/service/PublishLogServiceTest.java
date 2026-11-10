package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.PublishLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishLogServiceTest {

    private final PublishLogService logService = PublishLogService.getInstance();

    @BeforeEach
    @AfterEach
    void clearLogs() {
        logService.clearAllLogs();
    }

    @Test
    void addLog_prependsNewestFirst() {
        logService.addLog(PublishLog.success("First", "Blog", "blog-1", "post-1", "url-1", null));
        logService.addLog(PublishLog.success("Second", "Blog", "blog-1", "post-2", "url-2", null));

        List<PublishLog> logs = logService.getAllLogs();
        assertEquals(2, logs.size());
        assertEquals("Second", logs.get(0).getTitle());
        assertEquals("First", logs.get(1).getTitle());
    }

    @Test
    void successAndFailedFiltersSeparateCorrectly() {
        logService.addLog(PublishLog.success("Ok", "Blog", "blog-1", "post-1", "url-1", null));
        logService.addLog(PublishLog.failure("Bad", "Blog", "blog-1", null, "boom"));

        assertEquals(1, logService.getSuccessfulLogs().size());
        assertEquals(1, logService.getFailedLogs().size());
        assertTrue(logService.getFailedLogs().get(0).isFailed());
    }

    @Test
    void statistics_computeSuccessRate() {
        logService.addLog(PublishLog.success("Ok1", "Blog", "blog-1", "post-1", "url-1", null));
        logService.addLog(PublishLog.success("Ok2", "Blog", "blog-1", "post-2", "url-2", null));
        logService.addLog(PublishLog.failure("Bad", "Blog", "blog-1", null, "boom"));

        PublishLogService.PublishStatistics stats = logService.getStatistics();
        assertEquals(3, stats.total());
        assertEquals(2, stats.successful());
        assertEquals(1, stats.failed());
        assertEquals(200.0 / 3, stats.getSuccessRate(), 0.01);
    }

    @Test
    void clearAllLogs_removesEverything() {
        logService.addLog(PublishLog.success("Ok", "Blog", "blog-1", "post-1", "url-1", null));
        logService.clearAllLogs();
        assertTrue(logService.getAllLogs().isEmpty());
    }

    @Test
    void getLogsByBlog_filtersByBlogId() {
        logService.addLog(PublishLog.success("A", "Blog1", "blog-1", "post-1", "url-1", null));
        logService.addLog(PublishLog.success("B", "Blog2", "blog-2", "post-2", "url-2", null));

        List<PublishLog> blog1Logs = logService.getLogsByBlog("blog-1");
        assertEquals(1, blog1Logs.size());
        assertEquals("A", blog1Logs.get(0).getTitle());
    }
}
