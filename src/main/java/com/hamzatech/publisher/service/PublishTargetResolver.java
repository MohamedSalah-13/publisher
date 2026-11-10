package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.model.WordPressSite;

/**
 * يعيد بناء {@link PublishTarget} من هوية مخزَّنة (معرّف + اسم + منصة)، كما
 * تُخزَّن في {@code PublishJob}.
 * <p>
 * موقع ووردبريس محفوظ يُحلّ كاملًا محليًا. مدونة Blogger تُحلّ بلا رابط —
 * {@code BloggerPublisher} يستخدم الرابط في بناء رابط احتياطي فقط إذا فشل
 * نداء {@code getPostUrl}، فغيابه لا يمنع النشر نفسه.
 * <p>
 * نسخة مستقلة عمدًا عن نظيرتها الخاصة في {@code PublishLogController}، التي
 * تحلّ الرابط عبر نداء شبكي مُخبَّأ لأن شاشة السجلات تعرضه في كل صف. الطابور
 * لا يحتاج ذلك، فلم يستحق الأمر توحيدهما على حساب لمس كود مُختبَر يعمل.
 */
public final class PublishTargetResolver {

    private PublishTargetResolver() {
    }

    public static PublishTarget resolve(String targetId, String targetName) {
        WordPressSite wpSite = WordPressSiteService.getInstance().getSiteById(targetId);
        if (wpSite != null) {
            return PublishTarget.ofWordPress(wpSite);
        }
        return PublishTarget.ofBlogger(targetId, targetName, null);
    }
}
