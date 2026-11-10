package com.hamzatech.publisher;

import com.hamzatech.publisher.controller.Screens;
import com.hamzatech.publisher.service.FirstRun;
import com.hamzatech.publisher.util.AppIcons;
import com.hamzatech.publisher.util.AppWindow;
import com.hamzatech.publisher.util.DarkTitleBar;
import com.hamzatech.publisher.util.ThemeManager;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        ThemeManager.getInstance();
        DarkTitleBar.install();
        AppWindow.initialize(stage);
        AppIcons.applyTo(stage);
        stage.setResizable(true);

        // من لم يُعِدّ شيئًا بعد يبدأ بمعالج الإعداد؛ غيره بشاشة الدخول كما كان
        if (FirstRun.needsSetup()) {
            Screens.showSetupWizard(stage);
        } else {
            Screens.showLogin(stage);
        }

        stage.show();
    }

    public static void main(String[] args) {
        AppWindow.enablePreview();
        launch(args);
    }
}
