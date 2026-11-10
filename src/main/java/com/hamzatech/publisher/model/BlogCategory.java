package com.hamzatech.publisher.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * نموذج يمثل تصنيف المدونة
 */

@Setter
@Getter
public class BlogCategory implements Serializable {
    private String id;
    private String name;
    private String color; // لون التصنيف (اختياري)
    private List<String> blogIds; // معرّفات المدونات المرتبطة

    public BlogCategory(String id, String name, String color) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.blogIds = new ArrayList<>();
    }

    public void addBlog(String blogId) {
        if (!blogIds.contains(blogId)) {
            blogIds.add(blogId);
        }
    }

    public void removeBlog(String blogId) {
        blogIds.remove(blogId);
    }

    @Override
    public String toString() {
        return name;
    }
}
