package com.hamzatech.publisher.model;

import java.util.Collections;
import java.util.List;

/**
 * حصيلة دفعة نشر: ما نجح، ما فشل، ما تُخطّي لأنه منشور من قبل، وما لم يُحاوَل
 * أصلًا لأن المستخدم أوقف الدفعة.
 *
 * @param total     عدد العناوين في الملف
 * @param succeeded سجلات المقالات التي نُشرت
 * @param failed    سجلات المقالات التي فشلت، بأسباب الفشل
 * @param skipped   عناوين تُخطّيت لأنها منشورة على الوجهة نفسها من قبل
 * @param cancelled هل أوقف المستخدم الدفعة قبل انتهائها
 */
public record BatchPublishResult(int total,
                                  List<PublishLog> succeeded,
                                  List<PublishLog> failed,
                                  List<String> skipped,
                                  boolean cancelled) {

    public BatchPublishResult {
        succeeded = List.copyOf(succeeded);
        failed = List.copyOf(failed);
        skipped = List.copyOf(skipped);
    }

    public static BatchPublishResult nothingToPublish() {
        return new BatchPublishResult(0, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), false);
    }

    /** عدد العناوين التي لم تُحاوَل (يكون أكبر من صفر عند الإيقاف فقط). */
    public int notAttempted() {
        return total - succeeded.size() - failed.size() - skipped.size();
    }

    public BatchStatus status() {
        if (total == 0) {
            return BatchStatus.NOTHING_TO_PUBLISH;
        }
        if (cancelled) {
            return BatchStatus.CANCELLED;
        }
        if (!failed.isEmpty()) {
            return succeeded.isEmpty() ? BatchStatus.FAILED : BatchStatus.PARTIAL_SUCCESS;
        }
        // لا فشل: إمّا نُشر شيء، أو كان كل شيء منشورًا من قبل
        return succeeded.isEmpty() && !skipped.isEmpty() ? BatchStatus.ALL_SKIPPED : BatchStatus.SUCCESS;
    }

    /** عناوين المقالات الفاشلة، لإعادة المحاولة عليها وحدها. */
    public List<String> failedTitles() {
        return failed.stream().map(PublishLog::getTitle).toList();
    }
}
