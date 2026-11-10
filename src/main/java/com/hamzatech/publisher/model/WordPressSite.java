package com.hamzatech.publisher.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * موقع ووردبريس محفوظ (REST API + Application Password)
 */
@Setter
@Getter
public class WordPressSite implements Serializable {
    private String id;
    private String name;
    private String siteUrl;
    private String username;
    private String applicationPassword;

    public WordPressSite() {
    }

    public WordPressSite(String id, String name, String siteUrl, String username, String applicationPassword) {
        this.id = id;
        this.name = name;
        this.siteUrl = siteUrl;
        this.username = username;
        this.applicationPassword = applicationPassword;
    }

    @Override
    public String toString() {
        return name;
    }
}
