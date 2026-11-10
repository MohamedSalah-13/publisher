package com.hamzatech.publisher.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** A read-only view of publish attempts, including retries, for a selected period/target. */
public record DashboardMetrics(int total, long successful, long failed, long activeTargets,
                               List<DailyActivity> activity, List<PublishLog> recent) {
    public record DailyActivity(LocalDate date, long successful, long failed) { }

    public double successRate() {
        long resolved = successful + failed;
        return resolved == 0 ? 0 : successful * 100.0 / resolved;
    }

    public static DashboardMetrics summarize(List<PublishLog> logs, LocalDate today, int days, String targetId) {
        if (days < 0) throw new IllegalArgumentException("days must be non-negative");
        LocalDate first = days == 0 ? null : today.minusDays(days - 1L);
        List<PublishLog> filtered = logs.stream().filter(Objects::nonNull)
                .filter(log -> targetId == null || targetId.equals(log.getBlogId()))
                .filter(log -> {
                    if (log.getPublishDate() == null) return days == 0;
                    LocalDate date = log.getPublishDate().toLocalDate();
                    return !date.isAfter(today) && (first == null || !date.isBefore(first));
                }).toList();
        int chartDays = days == 0 ? 7 : days;
        LocalDate chartFirst = today.minusDays(chartDays - 1L);
        var daily = new HashMap<LocalDate, long[]>();
        for (PublishLog log : filtered) {
            if (log.getPublishDate() == null) continue;
            LocalDate date = log.getPublishDate().toLocalDate();
            if (date.isBefore(chartFirst) || date.isAfter(today)) continue;
            long[] counts = daily.computeIfAbsent(date, ignored -> new long[2]);
            if (log.isSuccess()) counts[0]++;
            else if (log.isFailed()) counts[1]++;
        }
        List<DailyActivity> activity = IntStream.range(0, chartDays).mapToObj(offset -> {
            LocalDate date = chartFirst.plusDays(offset);
            long[] counts = daily.getOrDefault(date, new long[2]);
            return new DailyActivity(date, counts[0], counts[1]);
        }).toList();
        var recent = filtered.stream().sorted(Comparator.comparing(PublishLog::getPublishDate,
                Comparator.nullsLast(Comparator.reverseOrder()))).limit(6).toList();
        return new DashboardMetrics(filtered.size(), filtered.stream().filter(PublishLog::isSuccess).count(),
                filtered.stream().filter(PublishLog::isFailed).count(),
                filtered.stream().map(PublishLog::getBlogId).filter(Objects::nonNull).distinct().count(),
                activity, recent);
    }

}
