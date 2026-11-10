package com.hamzatech.publisher.model;

/**
 * حالة عنوان واحد داخل {@link PublishJob}.
 */
public enum QueueItemStatus {
    /** لم يُحاوَل بعد. */
    PENDING,
    /** نُشر بنجاح. */
    SUCCEEDED,
    /** فشل النشر. */
    FAILED,
    /** تُخطّي لأنه منشور من قبل على الوجهة نفسها. */
    SKIPPED
}
