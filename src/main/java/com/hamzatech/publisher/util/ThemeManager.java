package com.hamzatech.publisher.util;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.prefs.Preferences;

public class ThemeManager {
    private static ThemeManager instance;
    private final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);
    private final BooleanProperty darkMode = new SimpleBooleanProperty();

    private static final String THEME_PREF_KEY = "app.theme";

    private ThemeManager() {
        boolean savedDark = prefs.get(THEME_PREF_KEY, "light").equals("dark");
        darkMode.set(savedDark);
        applyTheme();
        darkMode.addListener((obs, oldVal, newVal) -> applyTheme());
    }

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    public BooleanProperty darkModeProperty() {
        return darkMode;
    }

    public boolean isDarkMode() {
        return darkMode.get();
    }

    public void setDarkMode(boolean dark) {
        darkMode.set(dark);
        prefs.put(THEME_PREF_KEY, dark ? "dark" : "light");
    }

    public void toggle() {
        setDarkMode(!isDarkMode());
    }

    private void applyTheme() {
        Application.setUserAgentStylesheet(isDarkMode()
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }
}
