package com.hamzatech.publisher.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.NodeOrientation;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

public class LocaleManager {
    private static LocaleManager instance;
    private final ObjectProperty<Locale> currentLocale = new SimpleObjectProperty<>();
    private final Preferences prefs = Preferences.userNodeForPackage(LocaleManager.class);

    private static final String LOCALE_PREF_KEY = "app.locale";
    public static final Locale ARABIC = new Locale("ar");
    public static final Locale ENGLISH = Locale.ENGLISH;

    private LocaleManager() {
        // Load saved locale or default to Arabic
        String savedLocale = prefs.get(LOCALE_PREF_KEY, "ar");
        currentLocale.set(savedLocale.equals("ar") ? ARABIC : ENGLISH);
    }

    public static LocaleManager getInstance() {
        if (instance == null) {
            instance = new LocaleManager();
        }
        return instance;
    }

    public Locale getCurrentLocale() {
        return currentLocale.get();
    }

    public ObjectProperty<Locale> currentLocaleProperty() {
        return currentLocale;
    }

    public void setLocale(Locale locale) {
        currentLocale.set(locale);
        prefs.put(LOCALE_PREF_KEY, locale.getLanguage());
    }

    public ResourceBundle getResourceBundle() {
        return ResourceBundle.getBundle("com.hamzatech.publisher.i18n.messages", currentLocale.get());
    }

    public String getString(String key) {
        try {
            return getResourceBundle().getString(key);
        } catch (Exception e) {
            return key; // Return key if translation not found
        }
    }

    public String getString(String key, Object... args) {
        try {
            String pattern = getResourceBundle().getString(key);
            return String.format(pattern, args);
        } catch (Exception e) {
            return key;
        }
    }

    public boolean isRTL() {
        return currentLocale.get().equals(ARABIC);
    }

    /**
     * تطبيق اتجاه النص على Scene
     */
    public void applyOrientation(Scene scene) {
        if (scene != null) {
            scene.setNodeOrientation(isRTL() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        }
    }

    /**
     * تطبيق اتجاه النص على Stage
     */
    public void applyOrientation(Stage stage) {
        if (stage != null && stage.getScene() != null) {
            applyOrientation(stage.getScene());
        }
    }

    /**
     * الحصول على NodeOrientation حسب اللغة الحالية
     */
    public NodeOrientation getOrientation() {
        return isRTL() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT;
    }
}
