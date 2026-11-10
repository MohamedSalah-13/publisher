package com.hamzatech.publisher.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * ربط بين Label الأصلي والاسم المخصص
 */

@Setter
@Getter
public class LabelMapping implements Serializable {
    private String blogId;
    private String originalLabel;
    private String customName;
    private String color;

    public LabelMapping() {
    }

    public LabelMapping(String blogId, String originalLabel, String customName) {
        this.blogId = blogId;
        this.originalLabel = originalLabel;
        this.customName = customName;
    }

    @Override
    public String toString() {
        return customName != null ? customName : originalLabel;
    }
}
