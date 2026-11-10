package com.hamzatech.publisher.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.hamzatech.publisher.model.WordPressSite;
import com.hamzatech.publisher.util.AppPaths;
import com.hamzatech.publisher.util.SecretStore;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * خدمة لإدارة مواقع WordPress المحفوظة (REST API + Application Password)
 */
@Log4j2
public class WordPressSiteService {

    private static final String SITES_FILE = "wordpress_sites.json";
    private static final WordPressSiteService INSTANCE = new WordPressSiteService();

    private final Gson gson;
    private List<WordPressSite> sites;

    private WordPressSiteService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadSites();
    }

    public static WordPressSiteService getInstance() {
        return INSTANCE;
    }

    private void loadSites() {
        Path path = AppPaths.dataFile(SITES_FILE);

        if (Files.notExists(path)) {
            sites = new ArrayList<>();
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) {
                sites = new ArrayList<>();
                return;
            }

            Type type = new TypeToken<List<WordPressSite>>() {
            }.getType();
            List<WordPressSite> loaded = gson.fromJson(json, type);
            sites = (loaded != null) ? loaded : new ArrayList<>();
            revealPasswords();
        } catch (JsonParseException e) {
            backupCorruptedFile(path);
            sites = new ArrayList<>();
            log.warn("Invalid WordPress sites file detected and recreated. Reason: {}", e.getMessage());
        } catch (IOException e) {
            sites = new ArrayList<>();
            log.error("Failed to load WordPress sites: {}", e.getMessage());
        }
    }

    private static void backupCorruptedFile(Path original) {
        try {
            if (Files.exists(original)) {
                Path backup = original.resolveSibling("wordpress_sites_corrupted_" + System.currentTimeMillis() + ".json");
                Files.move(original, backup, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            log.warn("Failed to backup corrupted file, will attempt to create a new one later.");
        }
    }

    public void saveSites() throws IOException {
        Path path = AppPaths.dataFile(SITES_FILE);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            gson.toJson(protectedCopy(), writer);
        }
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * كلمات المرور تبقى في الذاكرة صريحة لأن Basic Auth يحتاجها، وتُشفَّر عند الكتابة فقط.
     * القيم القديمة الصريحة تُقرأ كما هي وتُكتب مشفَّرة عند أول حفظ.
     */
    private void revealPasswords() {
        for (WordPressSite site : sites) {
            String revealed = SecretStore.reveal(site.getApplicationPassword());
            if (revealed == null) {
                // ملف من جهاز أو حساب آخر: أفرغ الكلمة بدل تمرير قيمة تالفة إلى ووردبريس
                log.warn("Could not decrypt the application password for '{}'; re-enter it.", site.getName());
                site.setApplicationPassword("");
            } else {
                site.setApplicationPassword(revealed);
            }
        }
    }

    /**
     * نسخة للكتابة على القرص بكلمات مرور مشفَّرة، دون المساس بالنسخة التي في الذاكرة.
     */
    private List<WordPressSite> protectedCopy() {
        List<WordPressSite> copy = new ArrayList<>(sites.size());
        for (WordPressSite site : sites) {
            copy.add(new WordPressSite(
                    site.getId(),
                    site.getName(),
                    site.getSiteUrl(),
                    site.getUsername(),
                    SecretStore.protect(site.getApplicationPassword())));
        }
        return copy;
    }

    public List<WordPressSite> getAllSites() {
        return new ArrayList<>(sites);
    }

    public WordPressSite getSiteById(String id) {
        return sites.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public WordPressSite addSite(String name, String siteUrl, String username, String applicationPassword) throws IOException {
        WordPressSite site = new WordPressSite(UUID.randomUUID().toString(), name, normalizeUrl(siteUrl), username, applicationPassword);
        sites.add(site);
        saveSites();
        return site;
    }

    public void updateSite(WordPressSite site) throws IOException {
        WordPressSite existing = getSiteById(site.getId());
        if (existing == null) {
            log.warn("WordPress site does not exist, ignoring update: {}", site.getId());
            return;
        }
        site.setSiteUrl(normalizeUrl(site.getSiteUrl()));
        sites.remove(existing);
        sites.add(site);
        saveSites();
    }

    public void deleteSite(String id) throws IOException {
        sites.removeIf(s -> s.getId().equals(id));
        saveSites();
    }

    private static String normalizeUrl(String url) {
        return WordPressService.normalizeUrl(url);
    }
}
