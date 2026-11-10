module com.hamzatech.publisher {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.base;
    requires javafx.web;
    requires com.google.api.client.auth;
    requires com.google.api.client.extensions.java6.auth;
    requires com.google.api.client.extensions.jetty.auth;
    requires google.api.client;
    requires com.google.api.client;
    requires com.google.api.client.json.gson;
    requires com.google.api.services.blogger;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;

    // Needed for LocalServerReceiver (OAuth redirect listener)
    requires jdk.httpserver;
    requires com.google.gson;
    requires okhttp3;
    // OkHttp uses Kotlin at runtime; resolve its named module in JPMS launches.
    requires kotlin.stdlib;
    requires okio;
    requires java.prefs;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires static lombok;
    requires annotations;
    requires java.net.http;
    requires java.desktop;
    requires atlantafx.base;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;
    requires com.sun.jna;
    requires com.sun.jna.platform;

    opens com.hamzatech.publisher to javafx.fxml;
    exports com.hamzatech.publisher;
    exports com.hamzatech.publisher.controller;
    opens com.hamzatech.publisher.controller to javafx.fxml;

    // Allow Gson to reflectively access request/response DTOs in this package
    opens com.hamzatech.publisher.service to com.google.gson;
    opens com.hamzatech.publisher.model to com.google.gson, javafx.base;
}