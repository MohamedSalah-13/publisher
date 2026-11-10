package com.hamzatech.publisher.controller;

import com.hamzatech.publisher.service.BloggerService;
import com.hamzatech.publisher.util.LocaleManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ResourceBundle;

@Log4j2
public class LoginController {

    private final BloggerService bloggerService = BloggerService.getInstance();
    private final LocaleManager localeManager = LocaleManager.getInstance();
    @FXML
    private Button btnLogin;
    @FXML
    private VBox loadingBox;
    @FXML
    private Label lblError;
    @FXML
    private Label lblWelcome;
    @FXML
    private Label lblConnecting;
    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        log.info("Initializing LoginController");
        bundle = localeManager.getResourceBundle();
        updateTexts();

        // Listen for locale changes
        localeManager.currentLocaleProperty().addListener((obs, oldLocale, newLocale) -> {
            log.debug("Locale changed from {} to {}", oldLocale, newLocale);
            bundle = localeManager.getResourceBundle();
            updateTexts();
        });
    }

    private void updateTexts() {
        if (lblWelcome != null) lblWelcome.setText(bundle.getString("login.welcome"));
        if (btnLogin != null) btnLogin.setText(bundle.getString("login.button"));
        if (lblConnecting != null) lblConnecting.setText(bundle.getString("login.connecting"));
    }

    @FXML
    public void handleLogin() {
        log.info("User clicked login button");
        btnLogin.setDisable(true);
        loadingBox.setVisible(true);
        loadingBox.setManaged(true);
        lblError.setVisible(false);

        Task<Void> loginTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                log.debug("Starting login process");
                bloggerService.login();
                log.info("Login successful");
                return null;
            }
        };

        loginTask.setOnSucceeded(event -> {
            log.info("Login task succeeded, loading dashboard");
            loadingBox.setVisible(false);
            loadingBox.setManaged(false);
            loadDashboard();
        });

        loginTask.setOnFailed(event -> {
            Throwable exception = loginTask.getException();
            log.error("Login failed", exception);

            loadingBox.setVisible(false);
            loadingBox.setManaged(false);
            btnLogin.setDisable(false);

            lblError.setVisible(true);

            // رسالة واضحة عند غياب ملف OAuth credentials.json
            if (isMissingCredentials(exception)) {
                lblError.setText(bundle.getString("login.missing.credentials"));
            } else {
                String safeMsg = (exception != null && exception.getMessage() != null && !exception.getMessage().isBlank())
                        ? exception.getMessage()
                        : (exception != null ? exception.getClass().getSimpleName() : "Unknown error");
                lblError.setText(localeManager.getString("login.failed", safeMsg));
            }
        });

        new Thread(loginTask).start();
    }

    private boolean isMissingCredentials(Throwable t) {
        if (t == null) return false;

        // ... existing code ...
        if (t instanceof FileNotFoundException) return true;

        String msg = t.getMessage();
        if (msg != null) {
            // الرسالة التي نرميها من BloggerService عند عدم وجود الملف
            if (msg.contains("OAuth credentials not found")) return true;
            if (msg.contains("credentials.json")) return true;
            if (msg.contains("client_secret.json")) return true;
            if (msg.contains("Resource not found")) return true;
        }

        // افحص الأسباب (cause chain)
        Throwable c = t.getCause();
        while (c != null && c != t) {
            if (c instanceof FileNotFoundException) return true;
            String m = c.getMessage();
            if (m != null && (m.contains("OAuth credentials not found") || m.contains("credentials.json") || m.contains("client_secret.json"))) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    /**
     * ووردبريس لا يحتاج حساب Google؛ اللوحة تعرض المواقع المحفوظة وحدها حين لا
     * توجد جلسة Blogger.
     */
    @FXML
    public void handleContinueWithoutGoogle() {
        log.info("User continued without signing in to Google");
        loadDashboard();
    }

    @FXML
    public void handleSetupWizard() {
        Screens.showSetupWizard((Stage) btnLogin.getScene().getWindow());
    }

    private void loadDashboard() {
        try {
            log.debug("Loading dashboard view");
            Screens.showDashboard((Stage) btnLogin.getScene().getWindow());
            log.info("Dashboard loaded successfully");
        } catch (IOException e) {
            log.error("Failed to load dashboard", e);
            lblError.setText(bundle.getString("login.error.ui"));
            lblError.setVisible(true);
        }
    }
}
