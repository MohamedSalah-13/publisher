package com.hamzatech.publisher.http;

import lombok.extern.log4j.Log4j2;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * يعيد محاولة الطلبات التي تفشل فشلًا عابرًا، بتأخير متضاعف.
 * <p>
 * سبب وجودها: دفعة من عشرين مقالًا تعني عشرات النداءات المتلاحقة على OpenAI أو
 * Gemini، وحدّ المعدل ({@code 429}) أو انقطاع لحظة واحدة كان يُفشل المقال كاملًا —
 * بعد أن دُفع ثمن توليده. إعادة المحاولة هنا، على مستوى الطلب الواحد، أرخص وأسلم
 * من إعادتها على مستوى المقال: إعادة المقال تعني توليد محتواه من جديد، وقد تنشر
 * نسخة ثانية إذا كان الفشل قد وقع بعد إنشاء المنشور فعلًا.
 * <p>
 * ما يُعاد لأي طريقة: {@code 429} و{@code 408} و{@code 425}، لأنها تعني رفض الطلب
 * قبل تنفيذه. وما يُعاد للطرق غير المُغيِّرة فقط ({@code GET} وأخواتها): {@code 5xx}
 * وانتهاء مهلة القراءة، لأن الطلب قد يكون نُفِّذ ثم فشل الرد — وإعادة
 * {@code POST /wp/v2/posts} حينها تنشر المقال مرتين.
 * <p>
 * وما لا يُعاد أبدًا: أخطاء الطلب نفسه ({@code 4xx} عدا ما سبق) لأن تكرارها لن
 * يغيّر شيئًا، ومفتاح خاطئ يجب أن يظهر فورًا لا بعد أربع محاولات.
 * <p>
 * إذا أرسل الخادم ترويسة {@code Retry-After} فهي المصدر المعتمد، لأنها تقول متى
 * يُقبل الطلب فعلًا بدل تخميننا.
 */
@Log4j2
public class RetryInterceptor implements Interceptor {

    /**
     * رموز تعني أن الخادم رفض الطلب دون تنفيذه، فإعادته آمنة لأي method.
     */
    private static final Set<Integer> REJECTED_WITHOUT_PROCESSING = Set.of(408, 425, 429);

    /**
     * رموز قد يكون الخادم نفّذ الطلب قبلها ثم فشل في الرد، فلا تُعاد إلا لطلب
     * لا يُغيّر شيئًا بتكراره.
     */
    private static final Set<Integer> SERVER_ERRORS = Set.of(500, 502, 503, 504);

    /** الطرق التي لا يُحدث تكرارها أثرًا إضافيًا (RFC 9110). */
    private static final Set<String> IDEMPOTENT_METHODS =
            Set.of("GET", "HEAD", "PUT", "DELETE", "OPTIONS", "TRACE");

    private static final int DEFAULT_MAX_ATTEMPTS = 4;
    private static final long DEFAULT_INITIAL_DELAY_MS = 1_000L;
    private static final long MAX_DELAY_MS = 30_000L;

    private final int maxAttempts;
    private final long initialDelayMs;
    private final Sleeper sleeper;

    /** يسمح للاختبارات بقياس التأخير دون انتظاره فعلًا. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public RetryInterceptor() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_DELAY_MS, Thread::sleep);
    }

    public RetryInterceptor(int maxAttempts, long initialDelayMs, Sleeper sleeper) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.sleeper = sleeper;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        IOException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Response response = null;
            try {
                response = chain.proceed(request);

                if (!shouldRetry(request, response.code()) || attempt == maxAttempts) {
                    return response;
                }

                long delay = delayFor(attempt, response);
                log.warn("{} returned HTTP {}; retrying in {} ms (attempt {}/{}).",
                        request.url().host(), response.code(), delay, attempt, maxAttempts);
                // لا بد من إغلاق الرد قبل إعادة المحاولة وإلا سُرّب الاتصال
                response.close();
                response = null;
                sleepOrGiveUp(delay);

            } catch (IOException e) {
                if (response != null) {
                    response.close();
                }
                if (!shouldRetry(request, e) || attempt == maxAttempts) {
                    throw e;
                }
                lastFailure = e;
                long delay = backoffDelay(attempt);
                log.warn("{} failed with {}; retrying in {} ms (attempt {}/{}).",
                        request.url().host(), e.getClass().getSimpleName(), delay, attempt, maxAttempts);
                sleepOrGiveUp(delay);
            }
        }

        // لا يُبلَغ عمليًا: الحلقة ترجع أو ترمي في المحاولة الأخيرة
        throw lastFailure != null ? lastFailure
                : new IOException("Request failed after " + maxAttempts + " attempts: " + request.url());
    }

    /**
     * الانتظار قابل للمقاطعة: إيقاف الدفعة يجب ألا يعلق ثلاثين ثانية داخل backoff.
     */
    private void sleepOrGiveUp(long delayMs) throws IOException {
        try {
            sleeper.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Retry interrupted");
        }
    }

    private long delayFor(int attempt, Response response) {
        Long serverDelay = retryAfterMillis(response);
        return serverDelay != null ? Math.min(serverDelay, MAX_DELAY_MS) : backoffDelay(attempt);
    }

    /**
     * {@code Retry-After} تأتي بالثواني أو بتاريخ HTTP؛ نقبل الصيغة الرقمية فقط،
     * وهي التي تستخدمها واجهات OpenAI و Gemini و WordPress.
     */
    private static Long retryAfterMillis(Response response) {
        String header = response.header("Retry-After");
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header.trim());
            return seconds >= 0 ? seconds * 1000L : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * تضاعف مع عشوائية: دفعة تصطدم بحدّ المعدل تُنتج طلبات متزامنة، ولولا العشوائية
     * لعادت كلها في اللحظة نفسها فاصطدمت بالحدّ من جديد.
     */
    private long backoffDelay(int attempt) {
        long base = Math.min(initialDelayMs * (1L << (attempt - 1)), MAX_DELAY_MS);
        long jitter = ThreadLocalRandom.current().nextLong(base / 2 + 1);
        return base + jitter;
    }

    /**
     * هل نعيد المحاولة بعد رمز الحالة هذا؟
     * <p>
     * التفريق هنا ليس تدقيقًا نظريًا: {@code POST /wp/v2/posts} قد ينشئ المنشور فعلًا
     * ثم يفشل الخادم في إرسال الرد بـ {@code 502}. إعادة الطلب حينها تنشر المقال
     * مرتين — وهو أسوأ من الفشل الذي جئنا نعالجه. أما {@code 429} فيعني أن الطلب
     * رُفض قبل تنفيذه، فإعادته آمنة مهما كانت الطريقة.
     */
    private static boolean shouldRetry(Request request, int statusCode) {
        if (REJECTED_WITHOUT_PROCESSING.contains(statusCode)) {
            return true;
        }
        return SERVER_ERRORS.contains(statusCode) && isIdempotent(request);
    }

    /**
     * هل نعيد المحاولة بعد هذا العطل؟
     * <p>
     * {@link java.net.ConnectException} يعني أن الاتصال لم يُنشأ أصلًا فالطلب لم
     * يصل الخادم، وإعادته آمنة دائمًا. أما انتهاء مهلة القراءة فيعني أن الطلب
     * أُرسل وربما نُفِّذ، فلا يُعاد إلا لطريقة لا يُحدث تكرارها أثرًا.
     */
    private static boolean shouldRetry(Request request, IOException e) {
        if (e instanceof InterruptedIOException && !(e instanceof SocketTimeoutException)) {
            // مقاطعة متعمّدة (إيقاف الدفعة) وليست فشلًا في الشبكة
            return false;
        }
        if (e instanceof java.net.ConnectException) {
            return true;
        }
        boolean sentButUnanswered = e instanceof SocketTimeoutException || e instanceof java.net.SocketException;
        return sentButUnanswered && isIdempotent(request);
    }

    private static boolean isIdempotent(Request request) {
        return IDEMPOTENT_METHODS.contains(request.method().toUpperCase(java.util.Locale.ROOT));
    }
}
