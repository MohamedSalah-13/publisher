package com.hamzatech.publisher.provider;

import com.hamzatech.publisher.model.ArticleContent;
import com.hamzatech.publisher.model.PublishPlatform;
import com.hamzatech.publisher.model.PublishTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublisherRegistryTest {

    @AfterEach
    void restoreRegistry() {
        ProviderRegistry.resetToBuiltIns();
    }

    @Test
    void bothPlatformsAreRegistered() {
        assertEquals(PublishPlatform.BLOGGER, ProviderRegistry.publisher(PublishPlatform.BLOGGER).platform());
        assertEquals(PublishPlatform.WORDPRESS, ProviderRegistry.publisher(PublishPlatform.WORDPRESS).platform());
    }

    @Test
    void everyPlatformInTheEnumHasAPublisher() {
        // يمنع إضافة قيمة للـ enum دون تسجيل منصة لها
        for (PublishPlatform platform : PublishPlatform.values()) {
            assertEquals(platform, ProviderRegistry.publisher(platform).platform(), platform.name());
        }
    }

    @Test
    void lookupReturnsTheSameInstance() {
        assertSame(ProviderRegistry.publisher(PublishPlatform.WORDPRESS),
                ProviderRegistry.publisher(PublishPlatform.WORDPRESS));
    }

    @Test
    void aPlatformWithNoPublisherFailsLoudly_ratherThanPublishingToTheWrongPlace() {
        // بخلاف مزودي المحتوى والصور لا يوجد افتراضي معقول للمنصة:
        // النشر إلى المدونة الخطأ أسوأ بكثير من الفشل بوضوح
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ProviderRegistry.publisher(null));
        assertTrue(e.getMessage().contains("No publisher registered"), e.getMessage());
    }

    @Test
    void aThirdPlatformIsJustAClassPlusARegistration() {
        Publisher custom = new Publisher() {
            public PublishPlatform platform() {
                return PublishPlatform.WORDPRESS;
            }

            public PublishedPost publish(PublishTarget target, ArticleContent article, String category, boolean draft) {
                return new PublishedPost("custom-id", "https://example.com/custom");
            }
        };

        ProviderRegistry.register(custom);
        try {
            assertSame(custom, ProviderRegistry.publisher(PublishPlatform.WORDPRESS));
        } finally {
            ProviderRegistry.resetToBuiltIns();
        }
    }

    @Test
    void publishedPost_carriesBothTheIdAndTheUrl() {
        Publisher.PublishedPost post = new Publisher.PublishedPost("id-1", "https://example.com/p/1");
        assertEquals("id-1", post.postId());
        assertEquals("https://example.com/p/1", post.postUrl());
    }
}
