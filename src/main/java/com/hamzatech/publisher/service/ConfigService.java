package com.hamzatech.publisher.service;

import com.hamzatech.publisher.util.AppPaths;
import com.hamzatech.publisher.util.SecretStore;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.Set;

/**
 * خدمة إدارة الإعدادات والمفاتيح
 * تقوم بحفظ وقراءة إعدادات التطبيق من ملف config.properties
 * <p>
 * مفاتيح API تبقى في الذاكرة نصًّا صريحًا لأن التطبيق يحتاجها لاستدعاء الخدمات،
 * لكنها تُشفَّر عند حدود الملف: تُفَك عند التحميل وتُشفَّر عند الحفظ. بهذا لا تتغير
 * دلالة {@link #get} و{@link #set} لأي مستدعٍ، ويعمل الترحيل من تلقاء نفسه —
 * ملف قديم بقيم صريحة يُقرأ كما هو ويُكتب مشفَّرًا عند أول حفظ.
 */
@Log4j2
public class ConfigService {

    // مفاتيح الإعدادات
    public static final String OPENAI_API_KEY = "openai.api.key";
    public static final String GEMINI_API_KEY = "gemini.api.key";
    public static final String UNSPLASH_API_KEY = "unsplash.api.key";
    public static final String CLAUDE_API_KEY = "claude.api.key";
    public static final String AI_PROVIDER = "ai.provider"; // "openai" أو "gemini" أو "claude"
    public static final String OPENAI_MODEL = "openai.model";
    public static final String GEMINI_MODEL = "gemini.model"; // فارغ = اكتشاف تلقائي
    public static final String CLAUDE_MODEL = "claude.model";
    public static final String OPENAI_IMAGE_MODEL = "openai.image.model";
    public static final String OPENAI_IMAGE_QUALITY = "openai.image.quality"; // low | medium | high
    public static final String ARTICLE_LENGTH = "article.length"; // short | medium | long
    public static final String ARTICLE_FAQ = "article.faq"; // إضافة قسم أسئلة شائعة
    public static final String ARTICLE_INSTRUCTIONS = "article.instructions"; // تعليمات إضافية حرة
    public static final String POST_STATUS = "post.status"; // publish | draft
    public static final String SEO_AI = "seo.ai"; // توليد بيانات SEO بالذكاء الاصطناعي
    public static final String QUALITY_CHECK = "quality.check"; // فحص المقال قبل نشره
    public static final String INTERNAL_LINKS = "internal.links"; // روابط لمقالات سابقة ذات صلة
    public static final String IMAGE_PROVIDER = "image.provider"; // "unsplash" أو "dalle"
    public static final String PUBLISH_DELAY = "publish.delay"; // التأخير بين المقالات بالثواني
    public static final String SLUG_TYPE = "slug.type"; // نوع الـ slug: auto, custom, english
    public static final String TITLE_LANGUAGE = "title.language"; // arabic | english
    public static final String CONTENT_LANGUAGE = "content.language"; // arabic | english
    public static final String SKIP_DUPLICATES = "skip.duplicates"; // تخطّي ما نُشر من قبل
    public static final String TELEGRAM_BOT_TOKEN = "telegram.bot.token"; // توكن بوت من @BotFather
    public static final String FACEBOOK_PAGE_TOKEN = "facebook.page.token"; // Page (أو User) Access Token طويل الأمد
    private static final String CONFIG_FILE = "config.properties";

    /**
     * النماذج الافتراضية حين لا يحدد المستخدم نموذجًا. كانت مثبّتة داخل كل خدمة
     * ({@code gpt-3.5-turbo})، فتقادمها كان يعني تعديل الكود لا الإعدادات.
     */
    public static final String DEFAULT_OPENAI_MODEL = "gpt-4.1-mini";
    public static final String DEFAULT_CLAUDE_MODEL = "claude-sonnet-5-5";
    public static final String DEFAULT_OPENAI_IMAGE_MODEL = "gpt-image-1";
    public static final String DEFAULT_OPENAI_IMAGE_QUALITY = "medium";

    /** جودات صور {@code gpt-image-*} المقبولة؛ التكلفة تتضاعف عدة مرات بين كل درجة والتي تليها. */
    public static final Set<String> OPENAI_IMAGE_QUALITIES = Set.of("low", "medium", "high");

    public static final String POST_STATUS_PUBLISH = "publish";
    public static final String POST_STATUS_DRAFT = "draft";

    /** المفاتيح التي لا يجوز أن تُكتب على القرص بصورة صريحة. */
    private static final Set<String> SECRET_KEYS =
            Set.of(OPENAI_API_KEY, GEMINI_API_KEY, UNSPLASH_API_KEY, CLAUDE_API_KEY,
                    TELEGRAM_BOT_TOKEN, FACEBOOK_PAGE_TOKEN);

    private static final ConfigService INSTANCE = new ConfigService();
    private final Properties properties;

    private ConfigService() {
        properties = new Properties();
        loadConfig();
    }

    public static ConfigService getInstance() {
        return INSTANCE;
    }

    private static Path configPath() {
        return AppPaths.dataFile(CONFIG_FILE);
    }

    /**
     * تحميل الإعدادات من الملف وفكّ تشفير المفاتيح
     */
    private void loadConfig() {
        Path configFile = configPath();
        if (Files.notExists(configFile)) {
            return;
        }
        try (InputStream in = Files.newInputStream(configFile)) {
            properties.load(in);
        } catch (IOException e) {
            log.error("Failed to load config file: {}", e.getMessage());
            return;
        }

        for (String key : SECRET_KEYS) {
            String stored = properties.getProperty(key);
            if (stored == null) {
                continue;
            }
            String revealed = SecretStore.reveal(stored);
            if (revealed == null) {
                // تعذّر الفك (ملف من جهاز أو حساب آخر): أزل المفتاح بدل تمرير قيمة تالفة
                properties.remove(key);
                log.warn("Could not decrypt '{}'; re-enter it in the settings screen.", key);
            } else {
                properties.setProperty(key, revealed);
            }
        }
    }

    /**
     * حفظ الإعدادات إلى الملف بكتابة ذرّية، مع تشفير المفاتيح
     */
    public void saveConfig() throws IOException {
        Properties toStore = new Properties();
        properties.stringPropertyNames().forEach(key -> {
            String value = properties.getProperty(key);
            toStore.setProperty(key, SECRET_KEYS.contains(key) ? SecretStore.protect(value) : value);
        });

        Path target = configPath();
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            toStore.store(out, "Hamza Publisher Configuration");
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * الحصول على قيمة إعداد معين
     */
    public String get(String key) {
        return properties.getProperty(key);
    }

    /**
     * الحصول على قيمة إعداد مع قيمة افتراضية
     */
    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * تعيين قيمة إعداد
     */
    public void set(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }

    /**
     * التحقق من وجود مفتاح API معين
     */
    public boolean hasKey(String key) {
        String value = get(key);
        return value != null && !value.trim().isEmpty();
    }

    /**
     * الحصول على مزود الذكاء الاصطناعي الحالي
     */
    public String getAIProvider() {
        return get(AI_PROVIDER, "openai");
    }

    /**
     * الحصول على مزود الصور الحالي
     */
    public String getImageProvider() {
        return get(IMAGE_PROVIDER, "unsplash");
    }

    /**
     * الحصول على مفتاح OpenAI
     */
    public String getOpenAIKey() {
        // محاولة من الإعدادات أولاً، ثم من متغيرات البيئة
        String key = get(OPENAI_API_KEY);
        if (key == null || key.trim().isEmpty()) {
            key = System.getenv("OPENAI_API_KEY");
        }
        return key;
    }

    /**
     * الحصول على مفتاح Gemini
     */
    public String getGeminiKey() {
        String key = get(GEMINI_API_KEY);
        if (key == null || key.trim().isEmpty()) {
            key = System.getenv("GEMINI_API_KEY");
        }
        return key;
    }

    /**
     * الحصول على مفتاح Unsplash
     */
    public String getUnsplashKey() {
        String key = get(UNSPLASH_API_KEY);
        if (key == null || key.trim().isEmpty()) {
            key = System.getenv("UNSPLASH_API_KEY");
        }
        return key;
    }

    /**
     * الحصول على مفتاح Claude (Anthropic)
     */
    public String getClaudeKey() {
        String key = get(CLAUDE_API_KEY);
        if (key == null || key.trim().isEmpty()) {
            key = System.getenv("ANTHROPIC_API_KEY");
        }
        return key;
    }

    /**
     * توكن بوت تيليجرام الذي يشارك المقالات في القنوات
     */
    public String getTelegramBotToken() {
        String token = get(TELEGRAM_BOT_TOKEN);
        if (token == null || token.trim().isEmpty()) {
            token = System.getenv("TELEGRAM_BOT_TOKEN");
        }
        return token;
    }

    /**
     * توكن صفحة فيسبوك (أو توكن مستخدم يديرها) الذي يشارك المقالات على الصفحات
     */
    public String getFacebookPageToken() {
        String token = get(FACEBOOK_PAGE_TOKEN);
        if (token == null || token.trim().isEmpty()) {
            token = System.getenv("FACEBOOK_PAGE_TOKEN");
        }
        return token;
    }

    public String getOpenAIModel() {
        return get(OPENAI_MODEL, DEFAULT_OPENAI_MODEL);
    }

    /**
     * @return اسم نموذج Gemini، أو {@code null} لاكتشاف نموذج متاح تلقائيًا
     */
    public String getGeminiModel() {
        String model = get(GEMINI_MODEL);
        return model == null || model.isBlank() ? null : model.trim();
    }

    public String getOpenAIImageModel() {
        return get(OPENAI_IMAGE_MODEL, DEFAULT_OPENAI_IMAGE_MODEL);
    }

    /**
     * @return {@code "low"} أو {@code "medium"} أو {@code "high"}؛ أي قيمة أخرى في
     * الملف تعود {@code "medium"} لأن OpenAI يرفض الطلب كله بجودة لا يعرفها
     */
    public String getOpenAIImageQuality() {
        String quality = get(OPENAI_IMAGE_QUALITY, DEFAULT_OPENAI_IMAGE_QUALITY);
        return OPENAI_IMAGE_QUALITIES.contains(quality) ? quality : DEFAULT_OPENAI_IMAGE_QUALITY;
    }

    public String getClaudeModel() {
        return get(CLAUDE_MODEL, DEFAULT_CLAUDE_MODEL);
    }

    /**
     * @return {@code "short"} أو {@code "medium"} أو {@code "long"}
     */
    public String getArticleLength() {
        return get(ARTICLE_LENGTH, "medium");
    }

    public boolean isArticleFaq() {
        return "true".equals(get(ARTICLE_FAQ, "false"));
    }

    public void setArticleFaq(boolean include) {
        set(ARTICLE_FAQ, String.valueOf(include));
    }

    /**
     * @return تعليمات المستخدم الإضافية للكاتب الآلي، أو نص فارغ
     */
    public String getArticleInstructions() {
        return get(ARTICLE_INSTRUCTIONS, "");
    }

    /**
     * هل تُولَّد بيانات SEO (الوصف والوسوم والنص البديل) بنداء ذكاء اصطناعي إضافي؟
     * مفعّل افتراضيًا؛ تعطيله يوفّر النداء ويُبقي بديلًا مستخرجًا من المقال.
     */
    public boolean isSeoAiEnabled() {
        return !"false".equals(get(SEO_AI, "true"));
    }

    public void setSeoAiEnabled(boolean enabled) {
        set(SEO_AI, String.valueOf(enabled));
    }

    /**
     * هل يُفحص المقال المولَّد قبل نشره؟ مفعّل افتراضيًا — انظر {@code ArticleQualityCheck}.
     */
    public boolean isQualityCheckEnabled() {
        return !"false".equals(get(QUALITY_CHECK, "true"));
    }

    public void setQualityCheckEnabled(boolean enabled) {
        set(QUALITY_CHECK, String.valueOf(enabled));
    }

    /**
     * هل يُضاف إلى المقال قسم "اقرأ أيضًا" بروابط لمقالات سابقة ذات صلة؟ مفعّل افتراضيًا.
     */
    public boolean isInternalLinksEnabled() {
        return !"false".equals(get(INTERNAL_LINKS, "true"));
    }

    public void setInternalLinksEnabled(boolean enabled) {
        set(INTERNAL_LINKS, String.valueOf(enabled));
    }

    /**
     * @return {@link #POST_STATUS_PUBLISH} أو {@link #POST_STATUS_DRAFT}
     */
    public String getPostStatus() {
        return POST_STATUS_DRAFT.equals(get(POST_STATUS)) ? POST_STATUS_DRAFT : POST_STATUS_PUBLISH;
    }

    /**
     * الحصول على مدة التأخير بين المقالات (بالثواني)
     */
    public int getPublishDelay() {
        String delay = get(PUBLISH_DELAY, "5");
        try {
            return Integer.parseInt(delay);
        } catch (NumberFormatException e) {
            return 5; // القيمة الافتراضية
        }
    }

    /**
     * تعيين مدة التأخير بين المقالات
     */
    public void setPublishDelay(int seconds) {
        set(PUBLISH_DELAY, String.valueOf(seconds));
    }

    /**
     * الحصول على نوع الـ slug
     * @return "auto" أو "custom" أو "english"
     */
    public String getSlugType() {
        return get(SLUG_TYPE, "auto");
    }

    /**
     * تعيين نوع الـ slug
     */
    public void setSlugType(String type) {
        set(SLUG_TYPE, type);
    }

    /**
     * التحقق من تفعيل استخدام slug مخصص (للتوافق مع الكود القديم)
     */
    public boolean useCustomSlug() {
        String type = getSlugType();
        return "custom".equals(type) || "english".equals(type);
    }

    /**
     * التحقق من استخدام slug إنجليزي
     */
    public boolean useEnglishSlug() {
        return "english".equals(getSlugType());
    }

    /**
     * الحصول على لغة العنوان عند النشر
     */
    public String getTitleLanguage() {
        return get(TITLE_LANGUAGE, "arabic");
    }

    /**
     * تعيين لغة العنوان عند النشر
     */
    public void setTitleLanguage(String lang) {
        set(TITLE_LANGUAGE, lang);
    }

    /**
     * الحصول على لغة محتوى المقال
     */
    public String getContentLanguage() {
        return get(CONTENT_LANGUAGE, "arabic");
    }

    /**
     * تعيين لغة محتوى المقال
     */
    public void setContentLanguage(String lang) {
        set(CONTENT_LANGUAGE, lang);
    }

    /**
     * هل نتخطّى العناوين المنشورة من قبل على الوجهة نفسها؟ مفعّل افتراضيًا:
     * إعادة رفع ملف Excel بعد إضافة عنوان إليه لا ينبغي أن تنشر القديم من جديد.
     */
    public boolean isSkipDuplicates() {
        return !"false".equals(get(SKIP_DUPLICATES, "true"));
    }

    public void setSkipDuplicates(boolean skip) {
        set(SKIP_DUPLICATES, String.valueOf(skip));
    }
}
