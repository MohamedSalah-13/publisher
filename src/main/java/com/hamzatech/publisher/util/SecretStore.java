package com.hamzatech.publisher.util;

import com.sun.jna.platform.win32.Crypt32Util;
import lombok.extern.log4j.Log4j2;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * يشفّر الأسرار (مفاتيح API وكلمات مرور التطبيق) قبل كتابتها على القرص.
 * <p>
 * على ويندوز يستخدم DPAPI عبر {@code CryptProtectData}، ومفتاح التشفير مشتق من
 * حساب ويندوز نفسه ولا يُخزَّن في أي ملف. أثر ذلك عمليًا: من ينسخ
 * {@code config.properties} أو {@code wordpress_sites.json} إلى جهاز آخر — أو
 * يفتحه من حساب مستخدم آخر على نفس الجهاز — يجد قيمًا لا تُفك.
 * <p>
 * <b>ما لا يحميه:</b> برنامج يعمل تحت حساب المستخدم نفسه يستطيع فك التشفير تمامًا
 * كما يفعل التطبيق. DPAPI يحمي الملف المنقول أو المسروق، لا الجهاز المخترق.
 * <p>
 * القيم المشفَّرة تُكتب بالبادئة {@link #PREFIX}. أي قيمة بلا هذه البادئة تُعامَل
 * كنص صريح قديم وتُعاد كما هي، فيعمل الترحيل من تلقاء نفسه: القيم المخزَّنة قبل
 * هذا التغيير تُقرأ بشكل صحيح وتُكتب مشفَّرة عند أول حفظ.
 */
@Log4j2
public final class SecretStore {

    /** بادئة تميّز القيمة المشفَّرة عن نص صريح قديم. */
    static final String PREFIX = "dpapi:";

    private static final boolean AVAILABLE = isWindows();

    private SecretStore() {
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * هل التشفير مفعَّل فعلًا في هذه البيئة؟ يُستخدم للتحذير وللاختبارات.
     */
    public static boolean isEncryptionAvailable() {
        return AVAILABLE;
    }

    /**
     * يشفّر قيمة قبل تخزينها. القيم الفارغة تُعاد كما هي.
     * إذا تعذّر التشفير تُعاد القيمة كنص صريح مع تحذير — تعطيل حفظ الإعدادات
     * بالكامل ردٌّ أسوأ من كتابة ما كان يُكتب قبل هذا التغيير أصلًا.
     */
    public static String protect(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        if (!AVAILABLE) {
            log.warn("Secret encryption is unavailable on this platform; storing value as plain text.");
            return plaintext;
        }
        try {
            byte[] encrypted = Crypt32Util.cryptProtectData(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(encrypted);
        } catch (RuntimeException e) {
            log.error("Failed to encrypt a secret, storing it as plain text: {}", e.getMessage());
            return plaintext;
        }
    }

    /**
     * يفك قيمة مقروءة من القرص. القيمة بلا بادئة تُعاد كما هي (نص صريح قديم).
     */
    public static String reveal(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        String payload = stored.substring(PREFIX.length());
        try {
            byte[] decrypted = Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(payload));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            // يحدث إذا نُقل الملف من جهاز آخر أو من حساب مستخدم آخر
            log.error("Failed to decrypt a stored secret; re-enter it in the settings screen. Reason: {}",
                    e.getMessage());
            return null;
        }
    }

    /**
     * هل القيمة مخزَّنة مشفَّرة بالفعل؟ يُستخدم لمعرفة ما يحتاج إعادة كتابة.
     */
    public static boolean isProtected(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}
