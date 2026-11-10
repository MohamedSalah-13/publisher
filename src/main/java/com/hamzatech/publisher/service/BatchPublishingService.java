package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.BatchPublishResult;
import com.hamzatech.publisher.model.PublishJob;
import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.model.QueueItem;
import com.hamzatech.publisher.model.QueueItemStatus;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * ينشر دفعة عناوين ويعيد حصيلة صادقة عمّا حدث.
 * <p>
 * مستخرجة من {@code DashboardController} لسببين: الحلقة كانت تقرأ من عناصر
 * JavaFX داخل خيط خلفي، وكانت تُهمل نتيجة كل مقال فتظهر الدفعة ناجحة مهما فشل.
 * لا تعرف هذه الخدمة شيئًا عن JavaFX، فلا يمكن أن يتكرر أيٌّ من الأمرين هنا،
 * وصار المسار قابلًا للاختبار.
 */
@Log4j2
public class BatchPublishingService {

    /** يبلّغ المتصل بما يجري ليحدّث الواجهة (على خيط الواجهة، مسؤولية المتصل). */
    public interface ProgressListener {
        void onArticleStarted(int articleNumber, int total, String title);

        void onStage(int articleNumber, int total, ArticlePublishingService.Stage stage);

        void onArticleFinished(int articleNumber, int total, PublishLog result);

        /** عنوان تُخطّي لأنه منشور من قبل؛ لم تُستدعَ له {@code onArticleStarted}. */
        void onArticleSkipped(int articleNumber, int total, String title);

        /** ينتظر بين مقال وآخر. يرمي {@link InterruptedException} إذا أُوقف الانتظار. */
        void onWaiting(int secondsRemaining) throws InterruptedException;
    }

    private final ArticlePublisher publisher;
    private final DuplicateGuard duplicateGuard;

    /** يسمح للاختبارات باستبدال النشر الفعلي دون شبكة. */
    @FunctionalInterface
    public interface ArticlePublisher {
        PublishLog publish(String title, PublishTarget target, String category,
                           java.util.function.Consumer<ArticlePublishingService.Stage> onStage);
    }

    public BatchPublishingService(ArticlePublisher publisher) {
        this(publisher, DuplicateGuard.ALLOW_ALL);
    }

    public BatchPublishingService(ArticlePublisher publisher, DuplicateGuard duplicateGuard) {
        this.publisher = publisher;
        this.duplicateGuard = duplicateGuard;
    }

    /**
     * ينشئ خدمة تستخدم النشر الحقيقي، بخدمة ترجمة واحدة مشتركة لكل الدفعة.
     * مزودو المحتوى والصور والمنصات يحتفظون بعملائهم داخل {@code ProviderRegistry}
     * فلا حاجة لتمريرهم.
     */
    public static BatchPublishingService withSharedServices() {
        TranslationService translationService = new TranslationService();
        DuplicateGuard guard = ConfigService.getInstance().isSkipDuplicates()
                ? DuplicateGuard.fromPublishLog()
                : DuplicateGuard.ALLOW_ALL;
        return new BatchPublishingService(
                (title, target, category, onStage) ->
                        ArticlePublishingService.publishArticle(title, target, category, onStage, translationService),
                guard);
    }

    /**
     * ينشر كل العناوين ويعيد الحصيلة. لا يرمي عند فشل مقال — الفشل جزء من النتيجة.
     * <p>
     * تُسجَّل الدفعة في {@link PublishQueueService} أولًا، فتنجو من إغلاق التطبيق
     * أو انقطاعه — {@link #resumeJob} يكملها من حيث توقّفت.
     *
     * @param delaySeconds التأخير بين مقال وآخر، ولا يُنتظر بعد آخر مقال
     * @param cancelled    يُستشار قبل كل مقال وأثناء الانتظار
     */
    public BatchPublishResult publishAll(List<String> titles, PublishTarget target, String category,
                                          int delaySeconds, ProgressListener listener,
                                          BooleanSupplier cancelled) {
        if (titles == null || titles.isEmpty()) {
            log.warn("Nothing to publish: the file contained no titles.");
            return BatchPublishResult.nothingToPublish();
        }

        PublishJob job = PublishQueueService.getInstance().createJob(target, category, titles);
        return runJob(job, target, delaySeconds, listener, cancelled);
    }

    /**
     * يستأنف دفعة تُوقّفت من قبل — بإغلاق التطبيق، أو ضغط زر الإيقاف، أو انقطاع
     * غير متوقع — من حيث انتهت. العناصر المُنجزة تُحسب في الحصيلة دون إعادة
     * نشرها أو انتظار بعدها؛ النشر الفعلي يكمل من أول عنصر {@code PENDING}.
     */
    public BatchPublishResult resumeJob(PublishJob job, PublishTarget target, int delaySeconds,
                                         ProgressListener listener, BooleanSupplier cancelled) {
        return runJob(job, target, delaySeconds, listener, cancelled);
    }

    private BatchPublishResult runJob(PublishJob job, PublishTarget target, int delaySeconds,
                                       ProgressListener listener, BooleanSupplier cancelled) {
        List<QueueItem> items = job.getItems();
        String category = job.getCategory();
        int total = items.size();

        List<PublishLog> succeeded = new ArrayList<>();
        List<PublishLog> failed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        boolean stopped = false;

        for (int i = 0; i < total; i++) {
            if (isCancelled(cancelled)) {
                stopped = true;
                break;
            }

            int articleNumber = i + 1;
            QueueItem item = items.get(i);
            String title = item.getTitle();

            if (item.getStatus() != QueueItemStatus.PENDING) {
                // من استئناف: أُنجز في تشغيل سابق، فيُحسب في الحصيلة دون إعادة
                // نشره أو استهلاك دورة انتظار
                tallyResolvedItem(item, target, category, succeeded, failed, skipped);
                continue;
            }

            if (duplicateGuard.alreadyPublished(title, target)) {
                log.info("Skipping {}/{}: already published to {} — {}",
                        articleNumber, total, target.getName(), title);
                skipped.add(title);
                PublishQueueService.getInstance().updateItem(job.getId(), i, QueueItemStatus.SKIPPED, null);
                if (listener != null) {
                    listener.onArticleSkipped(articleNumber, total, title);
                }
                continue;
            }

            log.info("Processing article {}/{}: {}", articleNumber, total, title);
            if (listener != null) {
                listener.onArticleStarted(articleNumber, total, title);
            }

            PublishLog result = publisher.publish(title, target, category,
                    stage -> {
                        if (listener != null) {
                            listener.onStage(articleNumber, total, stage);
                        }
                    });

            if (result != null && result.isSuccess()) {
                succeeded.add(result);
                // يُلتقط التكرار داخل الدفعة نفسها: ملف Excel قد يحمل العنوان مرتين
                duplicateGuard.recordPublished(title, target);
                PublishQueueService.getInstance().updateItem(job.getId(), i, QueueItemStatus.SUCCEEDED, null);
            } else {
                PublishLog failure = result != null ? result
                        : PublishLog.failure(title, target.getName(), target.getId(), category,
                        "The publish call returned no result.");
                failed.add(failure);
                PublishQueueService.getInstance().updateItem(job.getId(), i, QueueItemStatus.FAILED, failure.getErrorMessage());
            }
            if (listener != null) {
                listener.onArticleFinished(articleNumber, total, result);
            }

            // لا انتظار إذا لم يبقَ عنصر PENDING بعد هذا — سواء لأنه آخر عنصر في
            // الملف، أو (عند الاستئناف) لأن كل ما بعده أُنجز في تشغيل سابق
            boolean morePendingAhead = items.subList(i + 1, total).stream()
                    .anyMatch(it -> it.getStatus() == QueueItemStatus.PENDING);
            if (morePendingAhead && delaySeconds > 0) {
                if (!waitBetweenArticles(delaySeconds, listener, cancelled)) {
                    stopped = true;
                    break;
                }
            }
        }

        PublishQueueService.getInstance().finish(job.getId());

        BatchPublishResult result = new BatchPublishResult(total, succeeded, failed, skipped, stopped);
        log.info("Batch finished: {} — {} succeeded, {} failed, {} already published, {} not attempted.",
                result.status(), succeeded.size(), failed.size(), skipped.size(), result.notAttempted());
        return result;
    }

    private static void tallyResolvedItem(QueueItem item, PublishTarget target, String category,
                                           List<PublishLog> succeeded, List<PublishLog> failed, List<String> skipped) {
        switch (item.getStatus()) {
            case SUCCEEDED -> succeeded.add(PublishLog.success(item.getTitle(), target.getName(), target.getId(),
                    null, null, category));
            case FAILED -> failed.add(PublishLog.failure(item.getTitle(), target.getName(), target.getId(),
                    category, item.getErrorMessage()));
            case SKIPPED -> skipped.add(item.getTitle());
            case PENDING -> throw new IllegalStateException("PENDING items must not reach tallyResolvedItem");
        }
    }

    /**
     * ينتظر ثانية بثانية ليستجيب الإيقاف فورًا بدل أن يعلق حتى انتهاء المهلة كاملة.
     *
     * @return false إذا أُوقفت الدفعة أثناء الانتظار
     */
    private boolean waitBetweenArticles(int delaySeconds, ProgressListener listener, BooleanSupplier cancelled) {
        for (int remaining = delaySeconds; remaining > 0; remaining--) {
            if (isCancelled(cancelled)) {
                return false;
            }
            try {
                if (listener != null) {
                    listener.onWaiting(remaining);
                } else {
                    Thread.sleep(1000L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !isCancelled(cancelled);
    }

    private static boolean isCancelled(BooleanSupplier cancelled) {
        return cancelled != null && cancelled.getAsBoolean();
    }
}
