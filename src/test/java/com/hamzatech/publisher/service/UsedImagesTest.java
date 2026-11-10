package com.hamzatech.publisher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.provider.ImageProvider;
import com.hamzatech.publisher.provider.ProviderRegistry;
import com.hamzatech.publisher.provider.UnsplashImageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * مقالات متشابهة العنوان على نفس المدونة لا تحمل نفس الصورة.
 */
class UsedImagesTest {

    private static final String BLOG_ID = "used-images-test-blog";
    private static final PublishTarget BLOGGER =
            PublishTarget.ofBlogger(BLOG_ID, "Blog", "https://blog.example.com");

    private static final String CAMEL = "https://images.unsplash.com/photo-camel";
    private static final String NILE = "https://images.unsplash.com/photo-nile";

    @AfterEach
    void cleanUp() {
        PublishLogService.getInstance().clearLogsByBlog(BLOG_ID);
        UsedImages.clearReservations();
        ProviderRegistry.resetToBuiltIns();
    }

    /** يأخذ أول صورة غير مستخدمة من قائمة ثابتة، كما يفعل Unsplash، بعد مهلة تكشف التداخل. */
    private static UsedImages.Selection firstUnusedOf(String... images) {
        return avoid -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (String image : images) {
                if (!avoid.contains(UsedImages.key(image))) {
                    return image;
                }
            }
            return images[0];
        };
    }

    private static JsonArray results(String... urls) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < urls.length; i++) {
            if (i > 0) json.append(',');
            json.append("{\"urls\":{\"regular\":\"").append(urls[i]).append("\"}}");
        }
        return JsonParser.parseString(json.append(']').toString()).getAsJsonArray();
    }

    private static PublishLog success(String imageUrl) {
        PublishLog log = PublishLog.success("t", "Blog", BLOG_ID, "1", "https://blog.example.com/p", "c");
        log.setImageUrl(imageUrl);
        return log;
    }

    @Test
    void key_ignoresTheQueryString_thatUnsplashChangesOnEveryRequest() {
        assertEquals(UsedImages.key(CAMEL + "?ixid=aaa&w=1080"), UsedImages.key(CAMEL + "?ixid=bbb&w=1080"));
    }

    @Test
    void pickUnused_skipsAnImageAlreadyOnTheBlog() {
        String picked = UnsplashService.pickUnused(
                results(CAMEL + "?ixid=new", NILE + "?ixid=new"), Set.of(UsedImages.key(CAMEL + "?ixid=old")));

        assertEquals(NILE + "?ixid=new", picked);
    }

    @Test
    void pickUnused_reusesTheTopImage_whenEveryResultIsUsed() {
        String picked = UnsplashService.pickUnused(results(CAMEL, NILE), Set.of(CAMEL, NILE));

        assertEquals(CAMEL, picked);
    }

    @Test
    void fromLogs_countsOnlySuccessfulArticlesWithAnImage() {
        PublishLog failed = PublishLog.failure("t", "Blog", BLOG_ID, "c", "boom");
        failed.setImageUrl(NILE);

        Set<String> used = UsedImages.fromLogs(List.of(success(CAMEL + "?ixid=x"), success(null), failed));

        assertEquals(Set.of(CAMEL), used);
    }

    @Test
    void loggable_dropsGeneratedImages_butKeepsLinks() {
        assertNull(UsedImages.loggable("data:image/png;base64,QUJD"));
        assertNull(UsedImages.loggable(null));
        assertEquals(CAMEL, UsedImages.loggable(CAMEL));
    }

    @Test
    void select_avoidsAnImageChosenEarlier_evenBeforeItsArticleIsLogged() throws IOException {
        String first = UsedImages.select(BLOGGER, firstUnusedOf(CAMEL, NILE));
        String second = UsedImages.select(BLOGGER, firstUnusedOf(CAMEL, NILE));

        assertEquals(CAMEL, first);
        assertEquals(NILE, second);
    }

    @Test
    void select_givesConcurrentArticlesOnOneTarget_differentImages() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> retry = () -> {
            start.await();
            return UsedImages.select(BLOGGER, firstUnusedOf(CAMEL, NILE));
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = pool.submit(retry);
            Future<String> b = pool.submit(retry);
            start.countDown();

            assertEquals(Set.of(CAMEL, NILE), Set.of(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS)));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void select_doesNotReserveForThePreview() throws IOException {
        UsedImages.select(null, firstUnusedOf(CAMEL, NILE));

        assertEquals(CAMEL, UsedImages.select(BLOGGER, firstUnusedOf(CAMEL, NILE)));
    }

    @Test
    void imageService_passesTheBlogsUsedImages_toTheProvider() throws IOException {
        PublishLogService.getInstance().addLog(success(CAMEL + "?ixid=x"));
        AtomicReference<Set<String>> seen = new AtomicReference<>();
        ProviderRegistry.register(new ImageProvider() {
            public String id() { return UnsplashImageProvider.ID; }
            public String displayName() { return "Unsplash"; }
            public boolean isConfigured() { return true; }
            public String imageForArticle(String articleTitle) { return NILE; }
            @Override
            public String imageForArticle(String articleTitle, Set<String> avoid) {
                seen.set(avoid);
                return NILE;
            }
        });

        new ImageService(UnsplashImageProvider.ID).getImageForArticle("Egypt", BLOGGER);

        assertTrue(seen.get().contains(CAMEL));
    }
}
