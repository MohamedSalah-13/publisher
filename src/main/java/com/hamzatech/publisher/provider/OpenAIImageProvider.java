package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.service.ConfigService;
import com.hamzatech.publisher.service.OpenAIService;

import java.io.IOException;

/**
 * صور مولَّدة بنماذج صور OpenAI ({@code gpt-image-1} افتراضيًا، والنموذج من
 * الإعدادات). يستخدم مفتاح OpenAI نفسه المستخدم لتوليد المقالات.
 * <p>
 * كان اسمه {@code DallEImageProvider} ومثبّتًا على {@code dall-e-3}. المعرّف بقي
 * {@code "dalle"} لأنه القيمة المخزَّنة في إعدادات المستخدمين وملفات تعريف الوجهات.
 *
 * @see OpenAIContentGenerator لسبب التهيئة الكسولة
 */
public class OpenAIImageProvider implements ImageProvider {

    public static final String ID = "dalle";

    private static final String PROMPT_TEMPLATE =
            "A professional, high-quality blog header image representing: %s. "
                    + "Modern, clean, visually appealing, photographic style. "
                    + "No text, letters, words or watermarks anywhere in the image.";

    private final ConfigService configService = ConfigService.getInstance();
    private OpenAIService client;
    private String clientKey;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "OpenAI Images";
    }

    @Override
    public boolean isConfigured() {
        String key = configService.getOpenAIKey();
        return key != null && !key.isBlank();
    }

    @Override
    public String imageForArticle(String articleTitle) throws IOException {
        return client().generateImage(String.format(PROMPT_TEMPLATE, articleTitle),
                configService.getOpenAIImageModel(), configService.getOpenAIImageQuality());
    }

    /** الصورة تعود بياناتٍ لا رابطًا — انظر {@link OpenAIService#generateImage}. */
    @Override
    public boolean returnsHostedUrl() {
        return false;
    }

    private synchronized OpenAIService client() {
        String key = configService.getOpenAIKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("مفتاح OpenAI غير موجود (مطلوب لتوليد الصور). الرجاء إضافته في الإعدادات.");
        }
        if (client == null || !key.equals(clientKey)) {
            client = new OpenAIService(key);
            clientKey = key;
        }
        return client;
    }
}
