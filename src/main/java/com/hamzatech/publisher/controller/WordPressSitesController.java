package com.hamzatech.publisher.controller;

import com.hamzatech.publisher.model.WordPressSite;
import com.hamzatech.publisher.service.WordPressService;
import com.hamzatech.publisher.service.WordPressSiteService;
import com.hamzatech.publisher.util.LocaleManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.ResourceBundle;

@Log4j2
public class WordPressSitesController {

    private final LocaleManager localeManager = LocaleManager.getInstance();
    private final WordPressSiteService siteService = WordPressSiteService.getInstance();
    private final WordPressService wordPressService = new WordPressService();

    @FXML
    private ListView<WordPressSite> sitesList;
    @FXML
    private TextField txtName;
    @FXML
    private TextField txtSiteUrl;
    @FXML
    private TextField txtUsername;
    @FXML
    private PasswordField txtApplicationPassword;
    @FXML
    private Button btnTestConnection;
    @FXML
    private Label lblStatus;

    private ResourceBundle bundle;

    @FXML
    public void initialize() {
        bundle = localeManager.getResourceBundle();

        sitesList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(WordPressSite site, boolean empty) {
                super.updateItem(site, empty);
                setText(empty || site == null ? null : site.getName() + " — " + site.getSiteUrl());
            }
        });

        loadSites();

        sitesList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                displaySite(newVal);
            }
        });
    }

    private void loadSites() {
        sitesList.getItems().setAll(siteService.getAllSites());
    }

    private void displaySite(WordPressSite site) {
        txtName.setText(site.getName());
        txtSiteUrl.setText(site.getSiteUrl());
        txtUsername.setText(site.getUsername());
        txtApplicationPassword.setText(site.getApplicationPassword());
    }

    @FXML
    public void handleAdd() {
        String name = txtName.getText().trim();
        // التطبيع مرة واحدة حتى يذهب العنوان نفسه إلى الفحص والحفظ والاتصال
        String siteUrl = WordPressService.normalizeUrl(txtSiteUrl.getText());
        String username = txtUsername.getText().trim();
        String appPassword = txtApplicationPassword.getText().trim();

        if (name.isEmpty() || siteUrl.isEmpty() || username.isEmpty() || appPassword.isEmpty()) {
            showError(bundle.getString("wordpress.error.fill.fields"));
            return;
        }

        if (rejectInsecureUrl(siteUrl)) {
            return;
        }
        txtSiteUrl.setText(siteUrl);

        try {
            siteService.addSite(name, siteUrl, username, appPassword);
            loadSites();
            clearFields();
            lblStatus.setText(bundle.getString("wordpress.success.added"));
        } catch (IOException e) {
            showError(java.text.MessageFormat.format(bundle.getString("wordpress.error.failed.save"), e.getMessage()));
        }
    }

    @FXML
    public void handleUpdate() {
        WordPressSite selected = sitesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError(bundle.getString("wordpress.error.select.site"));
            return;
        }

        String name = txtName.getText().trim();
        // التطبيع مرة واحدة حتى يذهب العنوان نفسه إلى الفحص والحفظ والاتصال
        String siteUrl = WordPressService.normalizeUrl(txtSiteUrl.getText());
        String username = txtUsername.getText().trim();
        String appPassword = txtApplicationPassword.getText().trim();

        if (name.isEmpty() || siteUrl.isEmpty() || username.isEmpty() || appPassword.isEmpty()) {
            showError(bundle.getString("wordpress.error.fill.fields"));
            return;
        }

        if (rejectInsecureUrl(siteUrl)) {
            return;
        }
        txtSiteUrl.setText(siteUrl);

        selected.setName(name);
        selected.setSiteUrl(siteUrl);
        selected.setUsername(username);
        selected.setApplicationPassword(appPassword);

        try {
            siteService.updateSite(selected);
            loadSites();
            lblStatus.setText(bundle.getString("wordpress.success.updated"));
        } catch (IOException e) {
            showError(java.text.MessageFormat.format(bundle.getString("wordpress.error.failed.save"), e.getMessage()));
        }
    }

    @FXML
    public void handleDelete() {
        WordPressSite selected = sitesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError(bundle.getString("wordpress.error.select.site"));
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(bundle.getString("wordpress.confirm.delete.title"));
        alert.setHeaderText(bundle.getString("wordpress.confirm.delete.message"));
        alert.setContentText(selected.getName());

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                siteService.deleteSite(selected.getId());
                loadSites();
                clearFields();
                lblStatus.setText(bundle.getString("wordpress.success.deleted"));
            } catch (IOException e) {
                showError(java.text.MessageFormat.format(bundle.getString("wordpress.error.failed.delete"), e.getMessage()));
            }
        }
    }

    @FXML
    public void handleTestConnection() {
        // التطبيع مرة واحدة حتى يذهب العنوان نفسه إلى الفحص والحفظ والاتصال
        String siteUrl = WordPressService.normalizeUrl(txtSiteUrl.getText());
        String username = txtUsername.getText().trim();
        String appPassword = txtApplicationPassword.getText().trim();

        if (siteUrl.isEmpty() || username.isEmpty() || appPassword.isEmpty()) {
            showError(bundle.getString("wordpress.error.fill.fields"));
            return;
        }

        if (rejectInsecureUrl(siteUrl)) {
            return;
        }
        txtSiteUrl.setText(siteUrl);

        btnTestConnection.setDisable(true);
        lblStatus.setText(bundle.getString("wordpress.testing"));

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                wordPressService.testConnection(siteUrl, username, appPassword);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            btnTestConnection.setDisable(false);
            lblStatus.setText(bundle.getString("wordpress.test.success"));
        });

        task.setOnFailed(e -> {
            btnTestConnection.setDisable(false);
            Throwable ex = task.getException();
            lblStatus.setText(java.text.MessageFormat.format(
                    bundle.getString("wordpress.test.failed"),
                    ex != null ? ex.getMessage() : bundle.getString("dashboard.error.unknown")
            ));
            log.warn("WordPress connection test failed", ex);
        });

        Thread th = new Thread(task, "wp-test-connection");
        th.setDaemon(true);
        th.start();
    }

    @FXML
    public void handleClose() {
        WorkspaceTabs.close(lblStatus);
    }

    private void clearFields() {
        txtName.clear();
        txtSiteUrl.clear();
        txtUsername.clear();
        txtApplicationPassword.clear();
        sitesList.getSelectionModel().clearSelection();
    }

    /**
     * ترفض إرسال كلمة مرور التطبيق عبر اتصال غير مشفّر.
     * القاعدة نفسها مطبَّقة داخل {@link WordPressService} كخط دفاع أخير؛ هنا فقط
     * لعرض رسالة مترجمة للمستخدم قبل أن يحفظ الموقع أو يختبر الاتصال.
     */
    private boolean rejectInsecureUrl(String normalizedUrl) {
        if (WordPressService.isSecureUrl(normalizedUrl)) {
            return false;
        }
        showError(bundle.getString("wordpress.error.insecure.url"));
        return true;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(bundle.getString("wordpress.error.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        log.error(message);
    }
}
