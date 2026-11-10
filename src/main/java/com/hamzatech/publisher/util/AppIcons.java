package com.hamzatech.publisher.util;

import com.hamzatech.publisher.MainApp;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * أيقونة التطبيق بكل أحجامها.
 * <p>
 * تُعطى النافذة عدة أحجام لا حجمًا واحدًا كبيرًا: نظام التشغيل يختار الأقرب لكل
 * موضع (شريط العنوان 16، شريط المهام 32/48، Alt+Tab أكبر)، وتصغير صورة كبيرة
 * إلى 16 بكسل كان يحوّلها إلى بقعة غير مقروءة.
 * <p>
 * مصدر الرسم في {@code docs/branding}، ومنه يُعاد توليد هذه الملفات و{@code tool.ico}.
 */
public final class AppIcons {

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static List<Image> icons;

    private AppIcons() {
    }

    public static synchronized List<Image> all() {
        if (icons == null) {
            List<Image> loaded = new ArrayList<>();
            for (int size : SIZES) {
                InputStream in = MainApp.class.getResourceAsStream("images/app-icon-" + size + ".png");
                if (in != null) {
                    loaded.add(new Image(in));
                }
            }
            icons = List.copyOf(loaded);
        }
        return icons;
    }

    public static void applyTo(Stage stage) {
        stage.getIcons().setAll(all());
    }

    /** الشعار لعرضه داخل الواجهة (شريط العنوان مثلًا). */
    public static ImageView logo(double size) {
        ImageView view = new ImageView(new Image(
                MainApp.class.getResourceAsStream("images/app-icon-64.png")));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }
}
