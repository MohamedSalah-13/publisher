package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.model.PublishPlatform;
import lombok.extern.log4j.Log4j2;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * السجل الذي يعرف كل المزودين المتاحين.
 * <p>
 * <b>لإضافة مزود جديد</b>: حقّق {@link ContentGenerator} في كلاس
 * جديد، وأضف سطرًا واحدًا في {@link #registerBuiltIns()}. لا يُعدَّل أي كلاس قائم.
 * <p>
 * الترتيب محفوظ ({@link LinkedHashMap}) لأن الواجهة تعرض المزودين بهذا الترتيب،
 * وأول مزود مسجَّل هو الافتراضي حين تحمل الإعدادات معرّفًا غير معروف — وهو ما
 * يحدث إذا فُتح ملف إعدادات كُتب بنسخة أحدث تعرف مزودًا لا نعرفه.
 */
@Log4j2
public final class ProviderRegistry {

    private static final Map<String, ContentGenerator> CONTENT_GENERATORS = new LinkedHashMap<>();
    private static final Map<String, ImageProvider> IMAGE_PROVIDERS = new LinkedHashMap<>();
    private static final Map<PublishPlatform, Publisher> PUBLISHERS = new EnumMap<>(PublishPlatform.class);
    private static final Map<String, SocialSharer> SOCIAL_SHARERS = new LinkedHashMap<>();

    static {
        registerBuiltIns();
    }

    private ProviderRegistry() {
    }

    private static void registerBuiltIns() {
        register(new OpenAIContentGenerator());
        register(new GeminiContentGenerator());
        register(new ClaudeContentGenerator());

        register(new UnsplashImageProvider());
        register(new OpenAIImageProvider());

        register(new BloggerPublisher());
        register(new WordPressPublisher());

        register(new TelegramSharer());
        register(new FacebookPageSharer());
    }

    /**
     * يعيد السجل إلى المزودين المدمجين وحدهم.
     * <p>
     * السجل حالة ثابتة لكل الـ JVM، فأي اختبار يسجّل مزودًا لا بد أن يعيد الحالة
     * بعده وإلا صار ترتيب تنفيذ الاختبارات مؤثرًا على نتائجها.
     */
    public static synchronized void resetToBuiltIns() {
        CONTENT_GENERATORS.clear();
        IMAGE_PROVIDERS.clear();
        PUBLISHERS.clear();
        SOCIAL_SHARERS.clear();
        registerBuiltIns();
    }

    public static void register(ContentGenerator generator) {
        CONTENT_GENERATORS.put(generator.id(), generator);
    }

    public static void register(ImageProvider provider) {
        IMAGE_PROVIDERS.put(provider.id(), provider);
    }

    public static void register(Publisher publisher) {
        PUBLISHERS.put(publisher.platform(), publisher);
    }

    public static void register(SocialSharer sharer) {
        SOCIAL_SHARERS.put(sharer.id(), sharer);
    }

    /** الشبكات الاجتماعية التي يُشارَك فيها المقال بعد نشره، بترتيب تسجيلها. */
    public static Collection<SocialSharer> socialSharers() {
        return List.copyOf(SOCIAL_SHARERS.values());
    }

    public static Collection<ContentGenerator> contentGenerators() {
        return List.copyOf(CONTENT_GENERATORS.values());
    }

    public static Collection<ImageProvider> imageProviders() {
        return List.copyOf(IMAGE_PROVIDERS.values());
    }

    /**
     * @param id المعرّف المخزَّن في الإعدادات؛ معرّف مجهول يرجع الافتراضي مع تحذير
     */
    public static ContentGenerator contentGenerator(String id) {
        ContentGenerator generator = CONTENT_GENERATORS.get(id);
        if (generator != null) {
            return generator;
        }
        ContentGenerator fallback = first(CONTENT_GENERATORS);
        log.warn("Unknown content generator '{}', falling back to '{}'.", id, fallback.id());
        return fallback;
    }

    /**
     * @param id المعرّف المخزَّن في الإعدادات؛ معرّف مجهول يرجع الافتراضي مع تحذير
     */
    public static ImageProvider imageProvider(String id) {
        ImageProvider provider = IMAGE_PROVIDERS.get(id);
        if (provider != null) {
            return provider;
        }
        ImageProvider fallback = first(IMAGE_PROVIDERS);
        log.warn("Unknown image provider '{}', falling back to '{}'.", id, fallback.id());
        return fallback;
    }

    /**
     * @throws IllegalStateException إذا لم تكن المنصة مسجَّلة — بخلاف المزودَين
     *                               الآخرين لا يوجد افتراضي معقول هنا، فالنشر إلى
     *                               منصة خاطئة أسوأ من الفشل بوضوح
     */
    public static Publisher publisher(PublishPlatform platform) {
        Publisher publisher = PUBLISHERS.get(platform);
        if (publisher == null) {
            throw new IllegalStateException("No publisher registered for platform: " + platform);
        }
        return publisher;
    }

    private static <T> T first(Map<String, T> providers) {
        if (providers.isEmpty()) {
            throw new IllegalStateException("No providers registered.");
        }
        return providers.values().iterator().next();
    }
}
