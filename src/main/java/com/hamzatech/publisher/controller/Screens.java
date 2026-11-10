package com.hamzatech.publisher.controller;

import com.hamzatech.publisher.util.AppWindow;
import com.hamzatech.publisher.util.LocaleManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ResourceBundle;

/**
 * الانتقال بين الشاشات الكاملة للنافذة الرئيسية: معالج الإعداد، تسجيل الدخول، اللوحة.
 * <p>
 * كان تحميل اللوحة داخل {@link LoginController} وحده؛ الآن يصل إليها معالج الإعداد
 * أيضًا، وتبدأ النافذة بأحد شاشتين، فجُمع ذلك هنا بدل نسخه في كل مكان.
 */
public final class Screens {

    private static final String VIEW = "/com/hamzatech/publisher/view/";

    private Screens() {
    }

    public static void showSetupWizard(Stage stage) {
        ResourceBundle bundle = LocaleManager.getInstance().getResourceBundle();
        show(stage, new SetupWizard(stage).root(), bundle.getString("app.title"));
    }

    public static void showLogin(Stage stage) throws IOException {
        ResourceBundle bundle = LocaleManager.getInstance().getResourceBundle();
        Parent root = new FXMLLoader(Screens.class.getResource(VIEW + "login.fxml"), bundle).load();
        show(stage, root, bundle.getString("app.title"));
    }

    public static void showDashboard(Stage stage) throws IOException {
        ResourceBundle bundle = LocaleManager.getInstance().getResourceBundle();
        Parent root = new FXMLLoader(Screens.class.getResource(VIEW + "dashboard.fxml"), bundle).load();
        show(stage, root, bundle.getString("dashboard.title"));
        stage.setWidth(1280);
        stage.setHeight(900);
        stage.centerOnScreen();
    }

    private static void show(Stage stage, Parent root, String title) {
        Scene scene = AppWindow.createScene(root);
        LocaleManager.getInstance().applyOrientation(scene);
        // مشهد جديد في نافذة ظاهرة يأخذ مقاس جذره المفضّل لا مقاس النافذة، والنافذة
        // لا تنكمش دون حدها الأدنى؛ فتظهر شاشة الدخول (400×560) في ركن نافذة أكبر
        // منها. يُعطى الجذر مقاس المشهد الذي يُستبدل، فيملأ النافذة كما كان
        Scene previous = stage.getScene();
        if (stage.isShowing() && previous != null && scene.getRoot() instanceof Region shell) {
            shell.setPrefSize(previous.getWidth(), previous.getHeight());
        }
        stage.setScene(scene);
        stage.setTitle(title);
    }
}
