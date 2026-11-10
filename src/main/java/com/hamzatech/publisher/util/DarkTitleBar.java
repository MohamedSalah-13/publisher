package com.hamzatech.publisher.util;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.ptr.IntByReference;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import lombok.extern.log4j.Log4j2;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * يجعل شريط عنوان Windows الأصلي داكنًا مع الوضع الداكن.
 * <p>
 * النافذة الرئيسية ترسم شريطها بنفسها ({@link AppWindow})، لكن النوافذ الأخرى —
 * الحوارات والتنبيهات — تستخدم شريط Windows، الذي يبقى فاتحًا رغم سمة التطبيق.
 * {@code Scene.Preferences.colorScheme} في JavaFX 25 لا يغيّره على Windows 10
 * (جُرِّب)، فيُضبط مباشرة عبر {@code DwmSetWindowAttribute} لكل نوافذ العملية:
 * عند ظهور أي نافذة، وعند تبديل السمة.
 * <p>
 * يُستدعى عبر {@link Function} لا عبر واجهة {@code Library}: الأخيرة تُنشئ وكيلًا
 * (proxy) لواجهة داخل وحدة JPMS المسمّاة، وهذا ما لا حاجة للمخاطرة به لدالة واحدة.
 * <p>
 * على غير Windows، أو إن فشل التحميل، لا يفعل شيئًا.
 */
@Log4j2
public final class DarkTitleBar {

    /** {@code DWMWA_USE_IMMERSIVE_DARK_MODE}: 20 منذ Windows 10 20H1، و19 في الإصدارات الأقدم. */
    private static final int DARK_MODE_ATTRIBUTE = 20;
    private static final int DARK_MODE_ATTRIBUTE_LEGACY = 19;

    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_FRAMECHANGED = 0x0020;
    private static final int WM_NCACTIVATE = 0x0086;

    private static Function setWindowAttribute;
    private static boolean installed;

    private DarkTitleBar() {
    }

    /** يُستدعى مرة واحدة عند بدء التطبيق، على خيط الواجهة. */
    public static synchronized void install() {
        if (installed || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return;
        }
        try {
            setWindowAttribute = NativeLibrary.getInstance("dwmapi").getFunction("DwmSetWindowAttribute");
        } catch (Throwable t) {
            log.debug("Dark title bars unavailable: {}", t.getMessage());
            return;
        }
        installed = true;

        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) {
                    window.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> apply());
                }
            }
        });
        Window.getWindows().forEach(w -> w.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> apply()));
        ThemeManager.getInstance().darkModeProperty().addListener((obs, was, now) -> apply());
        apply();
    }

    /** يطبّق السمة الحالية على كل نوافذ التطبيق الظاهرة. */
    private static void apply() {
        boolean dark = ThemeManager.getInstance().isDarkMode();
        // بعد الحدث لا داخله: النافذة لم تكتمل بعد لحظة WINDOW_SHOWN على بعض الأجهزة
        Platform.runLater(() -> {
            try {
                // نوافذ EXTENDED (الرئيسية) بلا شريط Windows أصلًا، وأزرارها يرسمها
                // JavaFX؛ لمس إطارها هنا كان يزيح تلك الأزرار ويُفسد ألوانها بعد تبديل
                // السمة (جُرِّب). تُعرف من عنوانها، فلا طريقة عامة لمعرفة HWND نافذة JavaFX
                Set<String> extendedTitles = new HashSet<>();
                for (Window window : Window.getWindows()) {
                    // العنوان الفارغ لا يُستثنى: كان سيستثني معه كل تنبيه بلا عنوان
                    if (window instanceof Stage stage && stage.getStyle() == StageStyle.EXTENDED
                            && stage.getTitle() != null && !stage.getTitle().isEmpty()) {
                        extendedTitles.add(stage.getTitle());
                    }
                }
                int pid = Kernel32.INSTANCE.GetCurrentProcessId();
                User32.INSTANCE.EnumWindows((hwnd, data) -> {
                    IntByReference owner = new IntByReference();
                    User32.INSTANCE.GetWindowThreadProcessId(hwnd, owner);
                    if (owner.getValue() == pid && User32.INSTANCE.IsWindowVisible(hwnd)
                            && !extendedTitles.contains(titleOf(hwnd))) {
                        setDark(hwnd, dark);
                    }
                    return true;
                }, null);
            } catch (Throwable t) {
                log.debug("Could not update title bars: {}", t.getMessage());
            }
        });
    }

    private static String titleOf(HWND hwnd) {
        // بطول العنوان الفعلي: مخزن ثابت كان يقطع العنوان الطويل فلا يطابق عنوان النافذة
        char[] buffer = new char[User32.INSTANCE.GetWindowTextLength(hwnd) + 1];
        int length = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
        return new String(buffer, 0, Math.max(0, length));
    }

    private static void setDark(HWND hwnd, boolean dark) {
        IntByReference value = new IntByReference(dark ? 1 : 0);
        int result = setWindowAttribute.invokeInt(new Object[]{hwnd, DARK_MODE_ATTRIBUTE, value, 4});
        if (result != 0) {
            setWindowAttribute.invokeInt(new Object[]{hwnd, DARK_MODE_ATTRIBUTE_LEGACY, value, 4});
        }
        // Windows 10 لا يعيد رسم الإطار من تلقاء نفسه: إعلان تغيّر الإطار ثم
        // تبديل حالة التنشيط ذهابًا وإيابًا يجبره على ذلك دون أن تفقد النافذة تركيزها
        User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0,
                SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
        boolean active = hwnd.equals(User32.INSTANCE.GetForegroundWindow());
        User32.INSTANCE.SendMessage(hwnd, WM_NCACTIVATE, new WPARAM(active ? 0 : 1), new LPARAM(0));
        User32.INSTANCE.SendMessage(hwnd, WM_NCACTIVATE, new WPARAM(active ? 1 : 0), new LPARAM(0));
    }
}
