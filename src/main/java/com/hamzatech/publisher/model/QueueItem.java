package com.hamzatech.publisher.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * عنوان واحد داخل {@link PublishJob}، بحالته.
 */
@Getter
@Setter
public class QueueItem implements Serializable {

    private String title;
    private QueueItemStatus status;
    private String errorMessage;

    public QueueItem() {
    }

    public QueueItem(String title) {
        this.title = title;
        this.status = QueueItemStatus.PENDING;
    }
}
