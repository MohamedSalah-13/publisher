package com.hamzatech.publisher;

import com.hamzatech.publisher.util.AppIcons;
import com.hamzatech.publisher.util.AppWindow;
import com.hamzatech.publisher.util.DarkTitleBar;
import com.hamzatech.publisher.util.LocaleManager;
import com.hamzatech.publisher.util.ThemeManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ResourceBundle;

public class MainApp extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        ThemeManager.getInstance();
        DarkTitleBar.install();
        AppWindow.initialize(stage);

        LocaleManager localeManager = LocaleManager.getInstance();
        ResourceBundle bundle = localeManager.getResourceBundle();

        // تحميل واجهة تسجيل الدخول كبداية
        FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("view/login.fxml"), bundle);
        Scene scene = AppWindow.createScene(fxmlLoader.load());

        // تطبيق الاتجاه حسب اللغة
        localeManager.applyOrientation(scene);
        AppIcons.applyTo(stage);
        stage.setTitle(bundle.getString("app.title"));
        stage.setScene(scene);
        stage.setResizable(true);

        stage.show();
    }

    public static void main(String[] args) {
        AppWindow.enablePreview();
        launch(args);
    }
}