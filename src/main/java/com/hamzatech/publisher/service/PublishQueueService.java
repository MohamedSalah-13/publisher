package com.hamzatech.publisher.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.hamzatech.publisher.model.PublishJob;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.model.QueueItem;
import com.hamzatech.publisher.model.QueueItemStatus;
import com.hamzatech.publisher.util.AppPaths;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * دفعات النشر التي لم تكتمل بعد، لتُستأنف بعد إغلاق التطبيق أو انقطاعه.
 * <p>
 * كل دفعة تُكتب على القرص عند إنشائها، وبعد كل مقال يُنشر منها — لا مرة واحدة
 * في النهاية، وإلا لم تُفِد النجاة من إغلاق مفاجئ في شيء. الدفعة تُحذف من
 * الملف تلقائيًا حين لا يبقى فيها عنصر {@code PENDING}؛ الملف بهذا يحمل دومًا
 * "ما بقي غير منجز" لا سجلًا تاريخيًا — ذاك دور {@code publish_logs.json}.
 */
@Log4j2
public class PublishQueueService {

    private static final String QUEUE_FILE = "publish_queue.json";
    private static final PublishQueueService INSTANCE = new PublishQueueService();

    private final Gson gson;
    private List<PublishJob> jobs;

    private PublishQueueService() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new PublishLogService.LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
        loadJobs();
    }

    public static PublishQueueService getInstance() {
        return INSTANCE;
    }

    private void loadJobs() {
        Path path = AppPaths.dataFile(QUEUE_FILE);

        if (Files.notExists(path)) {
            jobs = new ArrayList<>();
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) {
                jobs = new ArrayList<>();
                return;
            }

            Type type = new TypeToken<List<PublishJob>>() {
            }.getType();
            List<PublishJob> loaded = gson.fromJson(json, type);
            jobs = (loaded != null) ? loaded : new ArrayList<>();
        } catch (JsonParseException e) {
            backupCorruptedFile(path);
            jobs = new ArrayList<>();
            log.warn("Invalid publish queue file detected and recreated. Reason: {}", e.getMessage());
        } catch (IOException e) {
            jobs = new ArrayList<>();
            log.error("Failed to load the publish queue: {}", e.getMessage());
        }
    }

    private static void backupCorruptedFile(Path original) {
        try {
            if (Files.exists(original)) {
                Path backup = original.resolveSibling("publish_queue_corrupted_" + System.currentTimeMillis() + ".json");
                Files.move(original, backup, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            log.warn("Failed to backup corrupted file, will attempt to create a new one later.");
        }
    }

    private void saveJobs() {
        try {
            Path path = AppPaths.dataFile(QUEUE_FILE);
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                gson.toJson(jobs, writer);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // فشل حفظ الطابور لا يوقف النشر نفسه — أسوأ أثره أن الاستئناف لن يتوفر
            // بعد إغلاق غير متوقع، لا أن المقال الحالي يفشل بسببه
            log.error("Failed to save the publish queue", e);
        }
    }

    public synchronized PublishJob createJob(PublishTarget target, String category, List<String> titles) {
        PublishJob job = new PublishJob(UUID.randomUUID().toString(), target, category, titles);
        jobs.add(job);
        saveJobs();
        return job;
    }

    public synchronized void updateItem(String jobId, int index, QueueItemStatus status, String errorMessage) {
        PublishJob job = getJob(jobId);
        if (job == null) {
            return;
        }
        QueueItem item = job.getItems().get(index);
        item.setStatus(status);
        item.setErrorMessage(errorMessage);
        job.setUpdatedAt(LocalDateTime.now());
        saveJobs();
    }

    /**
     * يُستدعى عند انتهاء الدفعة، اكتملت أم أُوقفت. يحذفها من الملف إن لم يبقَ
     * فيها عنصر {@code PENDING} — وإلا بقيت "قابلة للاستئناف" في الشاشة رغم
     * اكتمالها فعلًا.
     */
    public synchronized void finish(String jobId) {
        PublishJob job = getJob(jobId);
        if (job != null && !job.hasPending()) {
            jobs.removeIf(j -> j.getId().equals(jobId));
        }
        saveJobs();
    }

    public synchronized PublishJob getJob(String jobId) {
        return jobs.stream().filter(j -> j.getId().equals(jobId)).findFirst().orElse(null);
    }

    /** الدفعات التي توقّفت وفيها عمل لم يكتمل بعد. */
    public synchronized List<PublishJob> getResumableJobs() {
        return jobs.stream().filter(PublishJob::hasPending).collect(Collectors.toList());
    }

    /** A consistent count while the publisher updates queue items in the background. */
    public synchronized long getPendingCount() {
        return jobs.stream().mapToLong(job -> job.countByStatus(QueueItemStatus.PENDING)).sum();
    }

    public synchronized void deleteJob(String jobId) {
        if (jobs.removeIf(j -> j.getId().equals(jobId))) {
            saveJobs();
        }
    }

    /** لأدوات الصيانة والاختبارات؛ لا زر لها في الواجهة. */
    public synchronized void clearAllJobs() {
        jobs.clear();
        saveJobs();
    }
}
