package com.hamzatech.publisher.service;

import com.hamzatech.publisher.http.HttpClients;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * يفحص مفتاح API قبل حفظه، بطلب مجاني لا يولّد شيئًا (قائمة النماذج أو ما يشبهها).
 * <p>
 * بدون هذا يكتشف المستخدم أن مفتاحه منسوخ ناقصًا عند أول دفعة نشر، برسالة خطأ
 * من المزود لا تقول له أين المشكلة. الفحص يجيب عن سؤال واحد: هل قبل المزود هذا
 * المفتاح؟ ولا يضمن وجود رصيد، فذلك لا يُعرف إلا بطلب مدفوع.
 * <p>
 * النتيجة قيمة لا استثناء: الواجهة تعرض رسالة مختلفة لكل حالة، ومفتاح مرفوض
 * ليس خطأ في البرنامج.
 */
public final class KeyCheck {

    public enum Outcome {
        /** المزود قبل المفتاح. */
        VALID,
        /** المزود رفض المفتاح نفسه (خاطئ، محذوف، أو منسوخ ناقصًا). */
        REJECTED,
        /** لم يصل الطلب إلى المزود أصلًا (لا اتصال، مهلة، DNS). */
        UNREACHABLE,
        /** المزود رد بخطأ لا يدل على المفتاح (عطل عنده، حد طلبات). */
        FAILED,
        /** هذا المزود لا يوفّر فحصًا. */
        UNSUPPORTED
    }

    /**
     * @param httpStatus رمز الرد، أو 0 حين لم يصل رد
     */
    public record Result(Outcome outcome, int httpStatus) {

        public static Result unsupported() {
            return new Result(Outcome.UNSUPPORTED, 0);
        }

        public boolean valid() {
            return outcome == Outcome.VALID;
        }

        /** هل يصح حفظ المفتاح رغم أن الفحص لم يؤكده؟ كل شيء إلا رفضًا صريحًا. */
        public boolean inconclusive() {
            return outcome != Outcome.VALID && outcome != Outcome.REJECTED;
        }
    }

    private static final String OPENAI_BASE = "https://api.openai.com";
    private static final String ANTHROPIC_BASE = "https://api.anthropic.com";
    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com";
    private static final String UNSPLASH_BASE = "https://api.unsplash.com";

    private static final Set<Integer> UNAUTHORIZED = Set.of(401, 403);
    /** Gemini يرد على مفتاح خاطئ بـ 400 (API_KEY_INVALID) لا 401. */
    private static final Set<Integer> GEMINI_REJECTED = Set.of(400, 401, 403);

    private KeyCheck() {
    }

    /** المستخدم ينتظر أمام الزر، فمهلة قصيرة خير من مهلة التوليد الطويلة. */
    private static final class Client {
        static final OkHttpClient INSTANCE = HttpClients.withReadTimeout(Duration.ofSeconds(20));
    }

    public static Result openAi(String apiKey) {
        return openAi(apiKey, OPENAI_BASE, Client.INSTANCE);
    }

    public static Result claude(String apiKey) {
        return claude(apiKey, ANTHROPIC_BASE, Client.INSTANCE);
    }

    public static Result gemini(String apiKey) {
        return gemini(apiKey, GEMINI_BASE, Client.INSTANCE);
    }

    public static Result unsplash(String accessKey) {
        return unsplash(accessKey, UNSPLASH_BASE, Client.INSTANCE);
    }

    // الصيغ التالية تأخذ أصل العنوان والعميل لتوجَّه في الاختبارات إلى خادم محلي

    static Result openAi(String apiKey, String baseUrl, OkHttpClient client) {
        return run(client, () -> new Request.Builder()
                .url(baseUrl + "/v1/models")
                .header("Authorization", "Bearer " + apiKey.trim()), UNAUTHORIZED);
    }

    static Result claude(String apiKey, String baseUrl, OkHttpClient client) {
        return run(client, () -> new Request.Builder()
                .url(baseUrl + "/v1/models")
                .header("x-api-key", apiKey.trim())
                .header("anthropic-version", "2023-06-01"), UNAUTHORIZED);
    }

    /**
     * المفتاح في ترويسة لا في الرابط: {@code RetryInterceptor} يضع الرابط كاملًا
     * في رسالة الفشل، و{@code ?key=} كان سيوصل المفتاح إلى ملف السجل.
     */
    static Result gemini(String apiKey, String baseUrl, OkHttpClient client) {
        return run(client, () -> new Request.Builder()
                .url(baseUrl + "/v1beta/models?pageSize=1")
                .header("x-goog-api-key", apiKey.trim()), GEMINI_REJECTED);
    }

    static Result unsplash(String accessKey, String baseUrl, OkHttpClient client) {
        return run(client, () -> new Request.Builder()
                .url(baseUrl + "/photos?per_page=1")
                .header("Authorization", "Client-ID " + accessKey.trim()), UNAUTHORIZED);
    }

    /**
     * @param request يُبنى داخل {@code try}: OkHttp يرفض الترويسة غير الصالحة عند
     *                بنائها لا عند الإرسال
     */
    private static Result run(OkHttpClient client, Supplier<Request.Builder> request, Set<Integer> rejectedCodes) {
        try (Response response = client.newCall(request.get().get().build()).execute()) {
            int code = response.code();
            if (response.isSuccessful()) {
                return new Result(Outcome.VALID, code);
            }
            return new Result(rejectedCodes.contains(code) ? Outcome.REJECTED : Outcome.FAILED, code);
        } catch (IOException | IllegalArgumentException e) {
            // IllegalArgumentException: مفتاح فيه محرف لا يصلح في ترويسة HTTP (سطر جديد
            // ملصوق مثلًا) — لم يُرسَل شيء، والسبب المفتاح نفسه
            return new Result(e instanceof IOException ? Outcome.UNREACHABLE : Outcome.REJECTED, 0);
        }
    }
}
