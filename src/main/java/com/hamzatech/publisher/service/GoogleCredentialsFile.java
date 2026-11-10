package com.hamzatech.publisher.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.hamzatech.publisher.util.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * فحص ملف {@code credentials.json} الذي يختاره المستخدم، ونسخه إلى مجلد البيانات.
 * <p>
 * كان المستخدم يُطلب منه وضع الملف يدويًا في مجلد لا يعرف مكانه، ويكتشف أنه نزّل
 * النوع الخاطئ (Web بدل Desktop) عند فشل تسجيل الدخول برسالة من Google. هنا
 * يُفحص الملف لحظة اختياره، ويُنسخ إلى المكان الذي يبحث فيه {@link BloggerService}.
 */
public final class GoogleCredentialsFile {

    public static final String FILE_NAME = "credentials.json";

    public enum Check {
        OK,
        /** تعذّرت قراءة الملف. */
        UNREADABLE,
        /** ليس JSON، أو JSON ليس ملف عميل OAuth. */
        NOT_CREDENTIALS,
        /** عميل من نوع Web application؛ تسجيل الدخول من سطح المكتب يحتاج Desktop app. */
        WEB_CLIENT
    }

    private GoogleCredentialsFile() {
    }

    public static Check check(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return Check.UNREADABLE;
        }

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                return Check.NOT_CREDENTIALS;
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            return Check.NOT_CREDENTIALS;
        }

        if (isClient(root.get("installed"))) {
            return Check.OK;
        }
        return isClient(root.get("web")) ? Check.WEB_CLIENT : Check.NOT_CREDENTIALS;
    }

    private static boolean isClient(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        JsonObject client = element.getAsJsonObject();
        return hasText(client, "client_id") && hasText(client, "client_secret");
    }

    private static boolean hasText(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() && !value.getAsString().isBlank();
    }

    /**
     * أين يُقرأ الملف؟ أول مكان يوجد فيه بهذا الترتيب: مجلد البيانات، مجلد الـ JAR،
     * مجلد التشغيل.
     * <p>
     * مجلد البيانات أولًا لأنه المكان الوحيد الذي يضع فيه المستخدم ملفًا باختياره
     * ({@link #install}). كان الأخير في الترتيب، فمن اختار ملفه في معالج الإعداد
     * وبجانب الـ JAR ملف مضمَّن في المثبّت رأى "قُبل الملف" وظل تسجيل الدخول يستخدم
     * الملف المضمَّن، ولا سبيل لاستبدال ملف مضمَّن لا يعمل.
     *
     * @param jarDir قد يكون {@code null} (تشغيل من IDE بلا JAR)
     */
    public static Optional<Path> locate(Path dataDir, Path jarDir, Path workingDir) {
        return Stream.of(dataDir, jarDir, workingDir)
                .filter(Objects::nonNull)
                .map(dir -> dir.resolve(FILE_NAME))
                .filter(Files::isRegularFile)
                .findFirst();
    }

    /**
     * ينسخ الملف إلى مجلد البيانات باسم {@value #FILE_NAME}، فيصير هو المستخدَم
     * ({@link #locate}).
     * <p>
     * مجلد البيانات لا مجلد البرنامج: الأخير لا يُكتب فيه دون صلاحيات في النسخة المثبّتة.
     *
     * @throws IllegalArgumentException إذا لم يجتز الملف {@link #check}
     */
    public static Path install(Path source) throws IOException {
        Check check = check(source);
        if (check != Check.OK) {
            throw new IllegalArgumentException("Not a usable OAuth client file: " + check);
        }
        Path target = AppPaths.dataDir().resolve(FILE_NAME);
        if (Files.exists(target) && Files.isSameFile(source, target)) {
            return target;
        }
        Path tmp = target.resolveSibling(FILE_NAME + ".tmp");
        Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }
}
