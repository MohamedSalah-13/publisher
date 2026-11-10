package com.hamzatech.publisher.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * دفعة نشر محفوظة على القرص، تنجو من إغلاق التطبيق أو انقطاعه.
 * <p>
 * لا حقل حالة عامًّا للدفعة نفسها (لا {@code RUNNING}/{@code PAUSED}): وجود
 * عنصر {@link QueueItemStatus#PENDING} واحد على الأقل هو ما يعني أن هذه الدفعة
 * قابلة للاستئناف، وإلا فلا شيء تبقّى ليُستأنف. عند بدء التطبيق لا توجد دفعة
 * "قيد التشغيل فعلًا" أصلًا — كل ما في الملف يعني بالضرورة أنه تُوقّف، سواء
 * بإغلاق التطبيق أو ضغط المستخدم زر الإيقاف أو انقطاع الكهرباء.
 */
@Getter
@Setter
public class PublishJob implements Serializable {

    private String id;
    private String targetId;
    private String targetName;
    private PublishPlatform platform;
    private String category;
    private List<QueueItem> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PublishJob() {
    }

    public PublishJob(String id, PublishTarget target, String category, List<String> titles) {
        this.id = id;
        this.targetId = target.getId();
        this.targetName = target.getName();
        this.platform = target.getPlatform();
        this.category = category;
        this.items = titles.stream().map(QueueItem::new).collect(Collectors.toList());
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean hasPending() {
        return items.stream().anyMatch(i -> i.getStatus() == QueueItemStatus.PENDING);
    }

    public long countByStatus(QueueItemStatus status) {
        return items.stream().filter(i -> i.getStatus() == status).count();
    }

    public int total() {
        return items.size();
    }
}
