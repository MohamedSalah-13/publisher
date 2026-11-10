package com.hamzatech.publisher.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hamzatech.publisher.model.PublishLog;
import lombok.extern.log4j.Log4j2;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * خدمة لإدارة سجلات النشر
 */
@Log4j2
public class PublishLogService {

    private static final String LOG_FILE_NAME = "publish_logs.json";
    private static final PublishLogService INSTANCE = new PublishLogService();
    private final Gson gson;
    private final Path logFilePath;

    private PublishLogService() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
        this.logFilePath = com.hamzatech.publisher.util.AppPaths.dataFile(LOG_FILE_NAME);
        log.info("PublishLogService initialized. Log file: {}", logFilePath.toAbsolutePath());
    }

    public static PublishLogService getInstance() {
        return INSTANCE;
    }

    /**
     * إضافة سجل نشر جديد
     */
    public synchronized void addLog(PublishLog loge) {
        try {
            List<PublishLog> logs = getAllLogs();
            logs.add(0, loge); // إضافة في البداية (الأحدث أولاً)

            saveLogs(logs);
            log.info("Log added: {} - {}", loge.getStatus(), loge.getTitle());
        } catch (Exception e) {
            log.error("Failed to add publish log", e);
        }
    }

    /**
     * يحدّث نتيجة المشاركة الاجتماعية على سجل نُشر من قبل.
     * <p>
     * السجل يُكتب قبل المشاركة لا بعدها: لو أُغلق التطبيق أثناءها لبقي المقال
     * مسجَّلًا منشورًا، فلا يعيد {@code DuplicateGuard} نشره عند الاستئناف. معرّف
     * المقال فريد داخل المدونة، فهو والمدونة يحدّدان السجل.
     */
    public synchronized void updateSharing(PublishLog entry) {
        if (entry.getPostId() == null) {
            return;
        }
        try {
            List<PublishLog> logs = getAllLogs();
            for (PublishLog existing : logs) {
                if (existing.isSuccess() && entry.getPostId().equals(existing.getPostId())
                        && java.util.Objects.equals(entry.getBlogId(), existing.getBlogId())) {
                    existing.setSharedTo(entry.getSharedTo());
                    existing.setShareError(entry.getShareError());
                    saveLogs(logs);
                    return;
                }
            }
            log.warn("No publish log found to record sharing for post {}", entry.getPostId());
        } catch (Exception e) {
            log.error("Failed to record sharing result", e);
        }
    }

    /**
     * الحصول على جميع السجلات
     */
    public synchronized List<PublishLog> getAllLogs() {
        try {
            if (!Files.exists(logFilePath)) {
                log.debug("Log file does not exist yet, returning empty list");
                return new ArrayList<>();
            }

            try (Reader reader = Files.newBufferedReader(logFilePath)) {
                Type listType = new TypeToken<ArrayList<PublishLog>>() {}.getType();
                List<PublishLog> logs = gson.fromJson(reader, listType);
                return logs != null ? logs : new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Failed to read publish logs", e);
            return new ArrayList<>();
        }
    }

    /**
     * الحصول على السجلات حسب المدونة
     */
    public List<PublishLog> getLogsByBlog(String blogId) {
        return getAllLogs().stream()
                .filter(log -> blogId.equals(log.getBlogId()))
                .collect(Collectors.toList());
    }

    /**
     * الحصول على السجلات الناجحة فقط
     */
    public List<PublishLog> getSuccessfulLogs() {
        return getAllLogs().stream()
                .filter(PublishLog::isSuccess)
                .collect(Collectors.toList());
    }

    /**
     * الحصول على السجلات الفاشلة فقط
     */
    public List<PublishLog> getFailedLogs() {
        return getAllLogs().stream()
                .filter(PublishLog::isFailed)
                .collect(Collectors.toList());
    }

    /**
     * حذف جميع السجلات
     */
    public synchronized void clearAllLogs() {
        try {
            if (Files.exists(logFilePath)) {
                Files.delete(logFilePath);
                log.info("All publish logs cleared");
            }
        } catch (Exception e) {
            log.error("Failed to clear publish logs", e);
        }
    }

    /**
     * حذف سجلات مدونة معينة
     */
    public synchronized void clearLogsByBlog(String blogId) {
        try {
            List<PublishLog> logs = getAllLogs();
            logs.removeIf(log -> blogId.equals(log.getBlogId()));
            saveLogs(logs);
            log.info("Cleared logs for blog: {}", blogId);
        } catch (Exception e) {
            log.error("Failed to clear logs for blog: {}", blogId, e);
        }
    }

    /**
     * الحصول على إحصائيات النشر
     */
    public PublishStatistics getStatistics() {
        List<PublishLog> logs = getAllLogs();
        long successCount = logs.stream().filter(PublishLog::isSuccess).count();
        long failedCount = logs.stream().filter(PublishLog::isFailed).count();

        return new PublishStatistics(
                logs.size(),
                (int) successCount,
                (int) failedCount
        );
    }

    /**
     * حفظ السجلات في الملف
     */
    private void saveLogs(List<PublishLog> logs) throws IOException {
        try (Writer writer = Files.newBufferedWriter(logFilePath)) {
            gson.toJson(logs, writer);
        }
    }

    /**
     * كلاس مساعد للإحصائيات
     */
    public record PublishStatistics(int total, int successful, int failed) {
        public double getSuccessRate() {
            return total == 0 ? 0 : (successful * 100.0) / total;
        }
    }

    /**
     * محول لـ LocalDateTime مع Gson. حزمي الرؤية عمدًا: {@code PublishQueueService}
     * يعيد استخدامه بدل نسخ نفس المحوّل.
     */
    static class LocalDateTimeAdapter extends com.google.gson.TypeAdapter<LocalDateTime> {
        @Override
        public void write(com.google.gson.stream.JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public LocalDateTime read(com.google.gson.stream.JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDateTime.parse(in.nextString());
        }
    }
}
