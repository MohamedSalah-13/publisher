package com.hamzatech.publisher;

import com.hamza.updater.AppUpdater;
import com.hamza.updater.Os;
import com.hamza.updater.ReleaseSource;
import com.hamza.updater.fx.UpdatePrompt;

import com.hamzatech.publisher.controller.Screens;
import com.hamzatech.publisher.service.FirstRun;
import com.hamzatech.publisher.util.AppIcons;
import com.hamzatech.publisher.util.AppPaths;
import com.hamzatech.publisher.util.AppWindow;
import com.hamzatech.publisher.util.DarkTitleBar;
import com.hamzatech.publisher.util.LocaleManager;
import com.hamzatech.publisher.util.ThemeManager;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {
    private UpdatePrompt updates;

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

        if (!Boolean.getBoolean("publisher.portable")) {
            AppUpdater updater = AppUpdater.builder("Hamza Publisher")
                    .source(ReleaseSource.github("MohamedSalah-13/publisher")
                            .asset("Hamza.Publisher-*.exe")
                            .assetFor(Os.LINUX, "hamza-publisher-*-linux-{arch}.deb")
                            .assetFor(Os.MAC, "hamza-publisher-*-macos-{arch}.dmg"))
                    .folder(AppPaths.dataDir().resolve("updates"))
                    .build();
            LocaleManager locale = LocaleManager.getInstance();
            updates = UpdatePrompt.create(updater, stage)
                    .language(locale.getCurrentLocale().getLanguage())
                    .start();
            locale.currentLocaleProperty().addListener((observable, previous, current) ->
                    updates.language(current.getLanguage()));
            stage.getProperties().put(UpdatePrompt.class, updates);
        }
    }

    @Override
    public void stop() {
        if (updates != null) {
            updates.stop();
        }
    }

    public static void main(String[] args) {
        AppWindow.enablePreview();
        launch(args);
    }
}
