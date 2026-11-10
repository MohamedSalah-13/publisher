package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.PublishPlatform;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.provider.ImageProvider;
import com.hamzatech.publisher.provider.ProviderRegistry;
import com.hamzatech.publisher.provider.UnsplashImageProvider;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;

/**
 * خدمة موحدة لإدارة الصور.
 * <p>
 * كانت تحمل سلسلة {@code if} تختار بين Unsplash و DALL-E وتدير عميل كل منهما.
 * صار ذلك مسؤولية {@link ImageProvider} و{@link ProviderRegistry}، وبقيت هذه
 * الواجهة كما هي حتى لا يتغيّر أي مستدعٍ.
 */
@Log4j2
public class ImageService {

    private final ConfigService configService;
    private final String providerOverride;

    public ImageService() {
        this(null);
    }

    /**
     * @param providerOverride معرّف مزوّد صريح (من ملف تعريف الوجهة مثلًا)، أو
     *                         {@code null} لاستخدام الإعداد العام
     */
    public ImageService(String providerOverride) {
        this.configService = ConfigService.getInstance();
        this.providerOverride = providerOverride;
    }

    /**
     * @return رابط صورة رأس المقال
     */
    public String getImageForArticle(String articleTitle) throws IOException {
        return provider().imageForArticle(articleTitle);
    }

    /**
     * صورة رأس تصلح للوجهة.
     * <p>
     * Blogger لا يرفع الصور عبر واجهته، فيحتاج رابطًا دائمًا. إن كان المزود
     * المختار يعيد بيانات الصورة لا رابطًا (صور OpenAI) تُؤخذ الصورة من Unsplash
     * لهذه الوجهة وحدها؛ وبلا مفتاح Unsplash يُنشر المقال بلا صورة مع تحذير —
     * أفضل من صورة مضمّنة تثقل المقال، أو رابط ينتهي بعد ساعة كما كان يحدث.
     *
     * @param target الوجهة، أو {@code null} (المعاينة) فتُستخدم صورة المزود كما هي
     * @return الرابط، أو {@code null} إن لم تتوفر صورة صالحة للوجهة
     */
    public String getImageForArticle(String articleTitle, PublishTarget target) throws IOException {
        ImageProvider provider = provider();
        if (target == null || target.getPlatform() != PublishPlatform.BLOGGER || provider.returnsHostedUrl()) {
            return UsedImages.select(target, avoid -> provider.imageForArticle(articleTitle, avoid));
        }

        ImageProvider unsplash = ProviderRegistry.imageProvider(UnsplashImageProvider.ID);
        if (unsplash.returnsHostedUrl() && unsplash.isConfigured()) {
            log.info("Blogger cannot host {} images; using {} for '{}'.",
                    provider.displayName(), unsplash.displayName(), articleTitle);
            return UsedImages.select(target, avoid -> unsplash.imageForArticle(articleTitle, avoid));
        }
        log.warn("Blogger cannot host {} images and no Unsplash key is set; publishing '{}' without a header image.",
                provider.displayName(), articleTitle);
        return null;
    }

    /**
     * هل المزود المختار جاهز للاستخدام (مفتاحه متوفر)؟
     */
    public boolean isConfigured() {
        return provider().isConfigured();
    }

    public String getCurrentProvider() {
        return providerOverride != null ? providerOverride : configService.getImageProvider();
    }

    private ImageProvider provider() {
        return ProviderRegistry.imageProvider(getCurrentProvider());
    }
}
