package com.hamzatech.publisher.model;

import java.util.List;

/**
 * بيانات تحسين محركات البحث المصاحبة للمقال.
 *
 * @param description وصف الميتا/المقتطف (حوالي 150 حرفًا)، أو {@code null}
 * @param tags        وسوم مقترحة، وقد تكون فارغة — لا {@code null}
 * @param imageAlt    النص البديل لصورة الرأس، أو {@code null}
 */
public record SeoMetadata(String description, List<String> tags, String imageAlt) {

    public SeoMetadata {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public static SeoMetadata empty() {
        return new SeoMetadata(null, List.of(), null);
    }

    public boolean hasDescription() {
        return description != null && !description.isBlank();
    }

    public boolean hasImageAlt() {
        return imageAlt != null && !imageAlt.isBlank();
    }
}
