package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.provider.ImageProvider;
import com.hamzatech.publisher.provider.OpenAIImageProvider;
import com.hamzatech.publisher.provider.ProviderRegistry;
import com.hamzatech.publisher.provider.UnsplashImageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * اختيار صورة تصلح للوجهة: Blogger لا يستضيف صورة مولَّدة.
 */
class ImageForTargetTest {

    private static final PublishTarget BLOGGER =
            PublishTarget.ofBlogger("blog-1", "Blog", "https://blog.example.com");
    private static final String GENERATED = "data:image/jpeg;base64,QUJD";
    private static final String UNSPLASH_URL = "https://images.unsplash.com/photo";

    private static ImageProvider fake(String id, String result, boolean hosted, boolean configured) {
        return new ImageProvider() {
            public String id() { return id; }
            public String displayName() { return id; }
            public boolean isConfigured() { return configured; }
            public String imageForArticle(String articleTitle) { return result; }
            @Override
            public boolean returnsHostedUrl() { return hosted; }
        };
    }

    private static void register(boolean unsplashConfigured) {
        ProviderRegistry.register(fake(OpenAIImageProvider.ID, GENERATED, false, true));
        ProviderRegistry.register(fake(UnsplashImageProvider.ID, UNSPLASH_URL, true, unsplashConfigured));
    }

    @AfterEach
    void resetRegistry() {
        ProviderRegistry.resetToBuiltIns();
    }

    @Test
    void blogger_getsAnUnsplashImage_insteadOfAGeneratedOne() throws IOException {
        register(true);

        assertEquals(UNSPLASH_URL, new ImageService(OpenAIImageProvider.ID).getImageForArticle("tea", BLOGGER));
    }

    @Test
    void blogger_withoutAnUnsplashKey_getsNoImage_ratherThanABrokenOne() throws IOException {
        register(false);

        assertNull(new ImageService(OpenAIImageProvider.ID).getImageForArticle("tea", BLOGGER));
    }

    @Test
    void wordPress_keepsTheGeneratedImage_sinceItUploadsIt() throws IOException {
        register(true);
        PublishTarget wordPress = PublishTarget.ofWordPress(new com.hamzatech.publisher.model.WordPressSite(
                "wp-1", "Site", "https://site.example.com", "admin", "pass"));

        assertEquals(GENERATED, new ImageService(OpenAIImageProvider.ID).getImageForArticle("tea", wordPress));
    }

    @Test
    void thePreview_withNoTarget_showsTheGeneratedImage() throws IOException {
        register(true);

        assertEquals(GENERATED, new ImageService(OpenAIImageProvider.ID).getImageForArticle("tea", null));
    }

    @Test
    void blogger_withUnsplashChosen_isUnchanged() throws IOException {
        register(true);

        assertEquals(UNSPLASH_URL, new ImageService(UnsplashImageProvider.ID).getImageForArticle("tea", BLOGGER));
    }
}
