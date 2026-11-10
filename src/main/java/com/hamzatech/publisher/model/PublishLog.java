package com.hamzatech.publisher.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * نموذج لتخزين سجلات النشر
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublishLog {

    private String title;              // عنوان المقال
    private String blogName;           // اسم المدونة
    private String blogId;             // معرف المدونة
    private String postId;             // معرف المقال المنشور
    private String postUrl;            // رابط المقال
    private String category;           // القسم/التصنيف
    private LocalDateTime publishDate; // تاريخ ووقت النشر
    private String status;             // الحالة: SUCCESS, FAILED
    private String errorMessage;       // رسالة الخطأ (إن وجدت)
    private boolean draft;             // حُفظ مسودة للمراجعة بدل نشره (السجلات القديمة: false)
    private List<String> sharedTo;     // الشبكات التي شورك فيها المقال بعد نشره (null: لا مشاركة)
    private String shareError;         // سبب فشل المشاركة؛ لا يجعل المقال نفسه فاشلًا
    private String imageUrl;           // رابط صورة الرأس، لتجنّب تكرارها على نفس المدونة (السجلات القديمة: null)

    /**
     * إنشاء سجل نشر ناجح
     */
    public static PublishLog success(String title, String blogName, String blogId,
                                     String postId, String postUrl, String category) {
        PublishLog log = new PublishLog();
        log.setTitle(title);
        log.setBlogName(blogName);
        log.setBlogId(blogId);
        log.setPostId(postId);
        log.setPostUrl(postUrl);
        log.setCategory(category);
        log.setPublishDate(LocalDateTime.now());
        log.setStatus("SUCCESS");
        return log;
    }

    /**
     * إنشاء سجل نشر فاشل
     */
    public static PublishLog failure(String title, String blogName, String blogId,
                                     String category, String errorMessage) {
        PublishLog log = new PublishLog();
        log.setTitle(title);
        log.setBlogName(blogName);
        log.setBlogId(blogId);
        log.setCategory(category);
        log.setPublishDate(LocalDateTime.now());
        log.setStatus("FAILED");
        log.setErrorMessage(errorMessage);
        return log;
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
