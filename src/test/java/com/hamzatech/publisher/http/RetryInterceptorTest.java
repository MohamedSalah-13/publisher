package com.hamzatech.publisher.http;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryInterceptorTest {

    private static final Request REQUEST =
            new Request.Builder().url("https://api.example.com/v1/thing").build();

    /** ما ترسله WordPressService لإنشاء منشور: طلب يُغيّر الحالة. */
    private static final Request POST_REQUEST = new Request.Builder()
            .url("https://example.com/wp-json/wp/v2/posts")
            .post(RequestBody.create("{}", MediaType.get("application/json")))
            .build();

    private final List<Long> slept = new ArrayList<>();

    /** يسجّل التأخير بدل انتظاره، فالاختبار يقيس السياسة لا يقضي الوقت. */
    private RetryInterceptor interceptor(int maxAttempts) {
        return new RetryInterceptor(maxAttempts, 1000L, slept::add);
    }

    @Test
    void aSuccessfulResponsePassesStraightThrough() throws IOException {
        FakeChain chain = FakeChain.returning(200);

        Response response = interceptor(4).intercept(chain);

        assertEquals(200, response.code());
        assertEquals(1, chain.attempts(), "no retry for a successful call");
        assertTrue(slept.isEmpty());
    }

    @Test
    void rateLimitIsRetried() throws IOException {
        // 429 هو السبب الأشيع لفشل دفعة كبيرة على OpenAI أو Gemini
        FakeChain chain = FakeChain.returning(429, 429, 200);

        Response response = interceptor(4).intercept(chain);

        assertEquals(200, response.code());
        assertEquals(3, chain.attempts());
        assertEquals(2, slept.size());
    }

    @Test
    void serverErrorsAreRetried() throws IOException {
        FakeChain chain = FakeChain.returning(503, 200);

        assertEquals(200, interceptor(4).intercept(chain).code());
        assertEquals(2, chain.attempts());
    }

    @Test
    void clientErrorsAreNotRetried() throws IOException {
        // مفتاح خاطئ يجب أن يظهر فورًا لا بعد أربع محاولات وثلاثين ثانية
        for (int status : new int[]{400, 401, 403, 404}) {
            FakeChain chain = FakeChain.returning(status, 200);
            assertEquals(status, interceptor(4).intercept(chain).code(), "status " + status);
            assertEquals(1, chain.attempts(), "status " + status + " must not be retried");
        }
        assertTrue(slept.isEmpty());
    }

    @Test
    void givingUpReturnsTheLastResponseRatherThanThrowing() throws IOException {
        FakeChain chain = FakeChain.returning(429, 429, 429, 429, 429);

        Response response = interceptor(3).intercept(chain);

        assertEquals(429, response.code(), "the caller still gets a response to report");
        assertEquals(3, chain.attempts());
        assertEquals(2, slept.size(), "no sleep after the final attempt");
    }

    @Test
    void transientNetworkFailuresAreRetried() throws IOException {
        FakeChain chain = FakeChain.throwingThen(new SocketTimeoutException("read timed out"), 200);
        assertEquals(200, interceptor(4).intercept(chain).code());
        assertEquals(2, chain.attempts());

        slept.clear();
        FakeChain refused = FakeChain.throwingThen(new ConnectException("connection refused"), 200);
        assertEquals(200, interceptor(4).intercept(refused).code());
        assertEquals(2, refused.attempts());
    }

    @Test
    void permanentNetworkFailuresAreNotRetried() {
        // عنوان لا وجود له لن يوجد بعد أربع محاولات
        FakeChain chain = FakeChain.throwingThen(new java.net.UnknownHostException("nope.invalid"), 200);

        assertThrows(IOException.class, () -> interceptor(4).intercept(chain));
        assertEquals(1, chain.attempts());
    }

    @Test
    void interruptionEndsTheCallInsteadOfRetrying() {
        // إيقاف الدفعة يقاطع الخيط؛ يجب ألا نعامله كعطل شبكة ونعيد المحاولة
        FakeChain chain = FakeChain.throwingThen(new InterruptedIOException("cancelled"), 200);

        assertThrows(InterruptedIOException.class, () -> interceptor(4).intercept(chain));
        assertEquals(1, chain.attempts());
    }

    @Test
    void interruptionDuringTheBackoffAbortsPromptly() {
        RetryInterceptor waiting = new RetryInterceptor(4, 1000L, millis -> {
            throw new InterruptedException("stopped");
        });
        FakeChain chain = FakeChain.returning(429, 200);

        assertThrows(InterruptedIOException.class, () -> waiting.intercept(chain));
        assertEquals(1, chain.attempts(), "must not start another attempt after being interrupted");
    }

    @Test
    void delayGrowsBetweenAttempts() throws IOException {
        interceptor(4).intercept(FakeChain.returning(429, 429, 429, 200));

        assertEquals(3, slept.size());
        assertTrue(slept.get(1) > slept.get(0), "delay must grow: " + slept);
        assertTrue(slept.get(2) > slept.get(1), "delay must grow: " + slept);
    }

    @Test
    void delayCarriesJitter_soARateLimitedBatchDoesNotRetryInLockstep() throws IOException {
        List<Long> firstDelays = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            slept.clear();
            interceptor(2).intercept(FakeChain.returning(429, 200));
            firstDelays.add(slept.get(0));
        }
        assertTrue(firstDelays.stream().distinct().count() > 1,
                "identical delays every time would send the whole batch back at once: " + firstDelays);
    }

    @Test
    void retryAfterHeaderWinsOverTheComputedBackoff() throws IOException {
        FakeChain chain = FakeChain.returningWithRetryAfter("7", 429, 200);

        interceptor(4).intercept(chain);

        assertEquals(List.of(7000L), slept, "the server said when to come back; honour it");
    }

    @Test
    void anUnparseableRetryAfterFallsBackToTheBackoff() throws IOException {
        // الترويسة تقبل تاريخ HTTP أيضًا؛ لا نفهمه فنرجع لحسابنا بدل أن نفشل
        FakeChain chain = FakeChain.returningWithRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT", 429, 200);

        interceptor(4).intercept(chain);

        assertEquals(1, slept.size());
        assertTrue(slept.get(0) >= 1000L, "should have used the computed backoff: " + slept);
    }

    @Test
    void aHugeRetryAfterIsCapped() throws IOException {
        FakeChain chain = FakeChain.returningWithRetryAfter("86400", 429, 200);

        interceptor(4).intercept(chain);

        assertEquals(List.of(30_000L), slept, "a day-long wait would hang the app");
    }

    @Test
    void serverErrorsAreNotRetriedForAPost_soAnArticleIsNeverPublishedTwice() throws IOException {
        // الخادم قد يكون أنشأ المنشور ثم فشل في الرد؛ إعادة الطلب تنشره مرة ثانية
        for (int status : new int[]{500, 502, 503, 504}) {
            slept.clear();
            FakeChain chain = FakeChain.returning(status, 200).forRequest(POST_REQUEST);

            assertEquals(status, interceptor(4).intercept(chain).code(), "status " + status);
            assertEquals(1, chain.attempts(), "a POST must not be repeated after " + status);
            assertTrue(slept.isEmpty());
        }
    }

    @Test
    void rateLimitIsStillRetriedForAPost() throws IOException {
        // 429 يعني أن الطلب رُفض قبل تنفيذه، فلا خطر من إعادته
        FakeChain chain = FakeChain.returning(429, 429, 200).forRequest(POST_REQUEST);

        assertEquals(200, interceptor(4).intercept(chain).code());
        assertEquals(3, chain.attempts());
    }

    @Test
    void readTimeoutIsNotRetriedForAPost() {
        // الطلب أُرسل فعلًا وقد يكون نُفِّذ؛ لا نعرف، فلا نكرره
        FakeChain chain = FakeChain.throwingThen(new SocketTimeoutException("read timed out"), 200)
                .forRequest(POST_REQUEST);

        assertThrows(SocketTimeoutException.class, () -> interceptor(4).intercept(chain));
        assertEquals(1, chain.attempts());
    }

    @Test
    void connectFailureIsStillRetriedForAPost() throws IOException {
        // الاتصال لم يُنشأ أصلًا، فالطلب لم يبلغ الخادم ولم يُنفَّذ
        FakeChain chain = FakeChain.throwingThen(new ConnectException("connection refused"), 200)
                .forRequest(POST_REQUEST);

        assertEquals(200, interceptor(4).intercept(chain).code());
        assertEquals(2, chain.attempts());
    }

    // ---------------------------------------------------------------

    /** سلسلة مزيّفة تعيد ردودًا مُعدّة سلفًا، فلا حاجة إلى خادم ولا شبكة. */
    private static final class FakeChain implements Interceptor.Chain {

        private final int[] statuses;
        private final IOException failure;
        private final String retryAfter;
        private final AtomicInteger calls = new AtomicInteger();
        private Request request = REQUEST;

        private FakeChain(int[] statuses, IOException failure, String retryAfter) {
            this.statuses = statuses;
            this.failure = failure;
            this.retryAfter = retryAfter;
        }

        FakeChain forRequest(Request request) {
            this.request = request;
            return this;
        }

        static FakeChain returning(int... statuses) {
            return new FakeChain(statuses, null, null);
        }

        static FakeChain returningWithRetryAfter(String header, int... statuses) {
            return new FakeChain(statuses, null, header);
        }

        /** يفشل في المحاولة الأولى ثم ينجح. */
        static FakeChain throwingThen(IOException failure, int status) {
            return new FakeChain(new int[]{status}, failure, null);
        }

        int attempts() {
            return calls.get();
        }

        @Override
        public Response proceed(Request request) throws IOException {
            int n = calls.incrementAndGet();
            if (failure != null && n == 1) {
                throw failure;
            }
            int index = Math.min(failure != null ? n - 2 : n - 1, statuses.length - 1);
            int status = statuses[index];

            Response.Builder builder = new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("status " + status)
                    .body(ResponseBody.create("{}", MediaType.get("application/json")));
            if (retryAfter != null && status == 429) {
                builder.header("Retry-After", retryAfter);
            }
            return builder.build();
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Connection connection() {
            return null;
        }

        @Override
        public Call call() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int connectTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withConnectTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int readTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withReadTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int writeTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withWriteTimeout(int timeout, TimeUnit unit) {
            return this;
        }
    }
}
