package com.hamzatech.publisher.model;

import lombok.Getter;

/**
 * وجهة نشر موحّدة تمثّل إما مدونة Blogger أو موقع WordPress، لعرضهما معاً
 * في نفس قائمة اختيار "المدونة" وتوجيه عملية النشر للخدمة المناسبة.
 */
@Getter
public class PublishTarget {
    private final String id;
    private final String name;
    private final String url;
    private final PublishPlatform platform;
    // بيانات إضافية خاصة بـ WordPress فقط (null لو المنصة Blogger)
    private final String wpUsername;
    private final String wpApplicationPassword;

    public static PublishTarget ofBlogger(String blogId, String name, String url) {
        return new PublishTarget(blogId, name, url, PublishPlatform.BLOGGER, null, null);
    }

    public static PublishTarget ofWordPress(WordPressSite site) {
        return new PublishTarget(site.getId(), site.getName(), site.getSiteUrl(), PublishPlatform.WORDPRESS,
                site.getUsername(), site.getApplicationPassword());
    }

    private PublishTarget(String id, String name, String url, PublishPlatform platform,
                           String wpUsername, String wpApplicationPassword) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.platform = platform;
        this.wpUsername = wpUsername;
        this.wpApplicationPassword = wpApplicationPassword;
    }

    @Override
    public String toString() {
        return platform == PublishPlatform.WORDPRESS ? name + " (WordPress)" : name;
    }
}
