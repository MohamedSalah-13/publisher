package com.hamzatech.publisher;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        com.hamzatech.publisher.util.AppWindow.enablePreview();
        Application.launch(MainApp.class, args);
    }
}
