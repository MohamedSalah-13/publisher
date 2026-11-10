package com.hamzatech.publisher.util;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * يحدّد أين يكتب التطبيق ملفاته.
 * <p>
 * كانت الخدمات تكتب بمسارات نسبية، أي داخل مجلد العمل — وهو مجلد المشروع أثناء
 * التطوير. النتيجة أن الإعدادات والأسرار تسكن بجوار الريبو ويسهل رفعها بالخطأ،
 * وأن تشغيل التطبيق من مجلد آخر يبدأ ببيانات فارغة.
 * <p>
 * الترتيب المعتمد لتحديد المجلد:
 * <ol>
 *   <li>الخاصية {@code publisher.data.dir} إن كانت مضبوطة — تستخدمها الاختبارات
 *       لعزل نفسها، ويستخدمها من يريد نسخة محمولة.</li>
 *   <li>على ويندوز: {@code %LOCALAPPDATA%\HamzaPublisher}</li>
 *   <li>على غيرها: {@code $XDG_DATA_HOME/hamza-publisher} وإلا {@code ~/.local/share/hamza-publisher}</li>
 * </ol>
 */
@Log4j2
public final class AppPaths {

    /** خاصية نظام لتجاوز مجلد البيانات (تضبطها الاختبارات على مجلد معزول). */
    public static final String DATA_DIR_PROPERTY = "publisher.data.dir";

    private static final String WINDOWS_FOLDER = "HamzaPublisher";
    private static final String UNIX_FOLDER = "hamza-publisher";

    private AppPaths() {
    }

    /**
     * مجلد بيانات التطبيق، مُنشأ إن لم يكن موجودًا.
     */
    public static Path dataDir() {
        Path dir = resolveDataDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Failed to create data directory {}: {}", dir, e.getMessage());
        }
        return dir;
    }

    private static Path resolveDataDir() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim()).toAbsolutePath();
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Paths.get(localAppData, WINDOWS_FOLDER);
            }
            return userHome().resolve(WINDOWS_FOLDER);
        }

        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Paths.get(xdg, UNIX_FOLDER);
        }
        return userHome().resolve(".local").resolve("share").resolve(UNIX_FOLDER);
    }

    private static Path userHome() {
        return Paths.get(System.getProperty("user.home", ".")).toAbsolutePath();
    }

    /**
     * مسار ملف داخل مجلد البيانات، مع ترحيل النسخة القديمة من مجلد العمل مرة واحدة.
     * <p>
     * الترحيل يحدث فقط إذا لم يكن الملف موجودًا في مجلد البيانات وكان موجودًا في
     * مجلد العمل، فلا يمكن أن يدهس بيانات قائمة.
     */
    public static Path dataFile(String fileName) {
        Path target = dataDir().resolve(fileName);
        if (Files.notExists(target)) {
            migrateFromWorkingDirectory(fileName, target);
        }
        return target;
    }

    /**
     * ينقل الملف القديم ولا يكتفي بنسخه.
     * <p>
     * الفرق ليس تجميليًا: مجلد المشروع كثيرًا ما يكون على قرص غير قرص
     * {@code %LOCALAPPDATA%}، و{@link StandardCopyOption#ATOMIC_MOVE} لا يعمل عبر
     * الأقراص. لو اكتفينا عندها بالنسخ لبقيت نسخة صريحة من الإعدادات في مجلد
     * المشروع إلى الأبد، وهو بالضبط ما ننقل الملفات هربًا منه. لذلك نجرّب النقل
     * العادي (نسخ ثم حذف داخليًا) قبل أي شيء آخر، ولا نلجأ للنسخ وحده إلا إذا
     * تعذّر الحذف — وحينها نحذّر بوضوح.
     */
    private static void migrateFromWorkingDirectory(String fileName, Path target) {
        Path legacy = Paths.get(fileName).toAbsolutePath();
        if (Files.notExists(legacy) || legacy.equals(target)) {
            return;
        }
        try {
            Files.move(legacy, target, StandardCopyOption.ATOMIC_MOVE);
            log.info("Moved {} into the app data directory: {}", fileName, target);
            return;
        } catch (IOException atomicFailed) {
            log.debug("Atomic move of {} not possible ({}), falling back to a plain move.",
                    fileName, atomicFailed.getMessage());
        }
        try {
            Files.move(legacy, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Moved {} into the app data directory: {}", fileName, target);
            return;
        } catch (IOException moveFailed) {
            log.debug("Plain move of {} failed ({}), falling back to copy + delete.",
                    fileName, moveFailed.getMessage());
        }
        try {
            Files.copy(legacy, target, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(legacy);
            log.info("Moved {} into the app data directory: {}", fileName, target);
        } catch (IOException copyFailed) {
            log.error("Could not migrate {} into {}. The old copy is still in the working "
                    + "directory and may contain secrets in plain text: {}",
                    fileName, target, copyFailed.getMessage());
        }
    }
}
