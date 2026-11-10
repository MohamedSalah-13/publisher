package com.hamzatech.publisher.http;

import okhttp3.OkHttpClient;

import java.time.Duration;

/**
 * المكان الوحيد الذي يُبنى فيه عميل HTTP.
 * <p>
 * كانت الخدمات الخمس تبني عملاءها بمهل متفرقة، فلم يكن هناك موضع واحد يُضاف فيه
 * سلوك مشترك مثل إعادة المحاولة. أي عميل جديد يجب أن يمرّ من هنا وإلا فقد
 * {@link RetryInterceptor}.
 */
public final class HttpClients {

    /** المهلة الافتراضية للاتصال. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private HttpClients() {
    }

    /**
     * عميل بمهلة قراءة قصيرة، للنداءات السريعة (بحث الصور، نداءات ووردبريس).
     */
    public static OkHttpClient standard() {
        return withReadTimeout(Duration.ofSeconds(60));
    }

    /**
     * عميل بمهلة قراءة طويلة، لتوليد النصوص والصور حيث يستغرق الرد دقائق.
     */
    public static OkHttpClient longRunning() {
        return withReadTimeout(Duration.ofMinutes(2));
    }

    public static OkHttpClient withReadTimeout(Duration readTimeout) {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT)
                .writeTimeout(CONNECT_TIMEOUT)
                .readTimeout(readTimeout)
                .addInterceptor(new RetryInterceptor())
                .build();
    }
}
