package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.service.ConfigService;
import com.hamzatech.publisher.service.KeyCheck;
import com.hamzatech.publisher.service.UnsplashService;

import java.io.IOException;
import java.util.Set;

/**
 * صور من مكتبة Unsplash.
 *
 * @see OpenAIContentGenerator لسبب التهيئة الكسولة
 */
public class UnsplashImageProvider implements ImageProvider {

    public static final String ID = "unsplash";

    private final ConfigService configService = ConfigService.getInstance();
    private UnsplashService client;
    private String clientKey;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Unsplash";
    }

    @Override
    public String apiKeySetting() {
        return ConfigService.UNSPLASH_API_KEY;
    }

    @Override
    public String apiKeyPage() {
        return "https://unsplash.com/oauth/applications";
    }

    @Override
    public KeyCheck.Result checkKey(String apiKey) {
        return KeyCheck.unsplash(apiKey);
    }

    @Override
    public boolean isConfigured() {
        String key = configService.getUnsplashKey();
        return key != null && !key.isBlank();
    }

    @Override
    public String imageForArticle(String articleTitle) throws IOException {
        return client().searchImage(articleTitle);
    }

    @Override
    public String imageForArticle(String articleTitle, Set<String> avoid) throws IOException {
        return client().searchImage(articleTitle, avoid);
    }

    private synchronized UnsplashService client() {
        String key = configService.getUnsplashKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("مفتاح Unsplash غير موجود. الرجاء إضافته في الإعدادات.");
        }
        if (client == null || !key.equals(clientKey)) {
            client = new UnsplashService(key);
            clientKey = key;
        }
        return client;
    }
}
