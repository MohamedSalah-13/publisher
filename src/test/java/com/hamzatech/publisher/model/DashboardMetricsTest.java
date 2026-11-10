package com.hamzatech.publisher.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DashboardMetricsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private PublishLog log(String target, int daysAgo, boolean success) {
        PublishLog result = success ? PublishLog.success("Article", target, target, "post", "https://example.com", "")
                : PublishLog.failure("Article", target, target, "", "failure");
        result.setPublishDate(TODAY.minusDays(daysAgo).atTime(12, 0));
        return result;
    }
    @Test void sevenDayWindowIncludesTodayAndSixDaysAgoButNotOlderOrFutureLogs() {
        var metrics = DashboardMetrics.summarize(List.of(log("a", 0, true), log("a", 6, false),
                log("a", 7, true), log("a", -1, true)), TODAY, 7, null);
        assertEquals(2, metrics.total());
        assertEquals(1, metrics.successful());
        assertEquals(1, metrics.failed());
        assertEquals(50, metrics.successRate());
        assertEquals(7, metrics.activity().size());
        assertEquals(1, metrics.activity().getFirst().failed());
        assertEquals(1, metrics.activity().getLast().successful());
    }
    @Test void filtersBothChartAndRecentResultsByTarget() {
        var metrics = DashboardMetrics.summarize(List.of(log("a", 0, true), log("b", 0, false)), TODAY, 30, "a");
        assertEquals(1, metrics.total());
        assertEquals(1, metrics.activeTargets());
        assertEquals(100, metrics.successRate());
        assertTrue(metrics.recent().stream().allMatch(row -> row.getBlogId().equals("a")));
        assertEquals(0, metrics.activity().stream().mapToLong(DashboardMetrics.DailyActivity::failed).sum());
    }
    @Test void allTimeIncludesOldAndUndatedLogsButChartRemainsLastSevenDays() {
        var undated = log("a", 0, false);
        undated.setPublishDate(null);
        var metrics = DashboardMetrics.summarize(List.of(log("a", 100, true), undated), TODAY, 0, null);
        assertEquals(2, metrics.total());
        assertEquals(7, metrics.activity().size());
        assertEquals(0, metrics.activity().stream().mapToLong(DashboardMetrics.DailyActivity::successful).sum());
        assertNull(metrics.recent().getLast().getPublishDate());
        assertEquals(0, DashboardMetrics.summarize(List.of(undated), TODAY, 7, null).total());
    }
    @Test void sortsRecentActivityByTimestampAndLimitsItToSix() {
        List<PublishLog> logs = new ArrayList<>();
        for (int days = 9; days >= 0; days--) logs.add(log("a", days, true));
        var metrics = DashboardMetrics.summarize(logs, TODAY, 30, null);
        assertEquals(6, metrics.recent().size());
        assertEquals(TODAY, metrics.recent().getFirst().getPublishDate().toLocalDate());
        assertEquals(TODAY.minusDays(5), metrics.recent().getLast().getPublishDate().toLocalDate());
    }
    @Test void unknownStatusesDoNotDistortTheSuccessRateAndRetriesAreCountedAsAttempts() {
        var unknown = log("b", 0, true);
        unknown.setStatus("OTHER");
        var metrics = DashboardMetrics.summarize(List.of(log("a", 0, true), log("a", 0, false), unknown), TODAY, 7, null);
        assertEquals(3, metrics.total());
        assertEquals(2, metrics.activeTargets());
        assertEquals(50, metrics.successRate());
    }
    @Test void emptyActivityHasZeroCountsAndACompleteDateAxis() {
        var metrics = DashboardMetrics.summarize(List.of(), TODAY, 30, null);
        assertEquals(0, metrics.total());
        assertEquals(0, metrics.successRate());
        assertEquals(30, metrics.activity().size());
        assertTrue(metrics.recent().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> DashboardMetrics.summarize(List.of(), TODAY, -1, null));
    }
}