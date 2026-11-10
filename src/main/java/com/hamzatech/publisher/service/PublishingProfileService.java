package com.hamzatech.publisher.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.hamzatech.publisher.model.EffectiveSettings;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.model.PublishingProfile;
import com.hamzatech.publisher.util.AppPaths;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * إعدادات نشر خاصة بكل وجهة، تُغلّب على الإعدادات العامة عند وجودها.
 * <p>
 * تحتاج مثلًا موقعًا يُنشر إليه بالإنجليزية دائمًا بينما البقية عربية، أو مدونة
 * تُستخدم فيها Gemini بدل OpenAI الافتراضية — دون تبديل الإعدادات العامة قبل كل
 * دفعة والتراجع عنها بعدها.
 */
@Log4j2
public class PublishingProfileService {

    private static final String PROFILES_FILE = "publishing_profiles.json";
    private static final PublishingProfileService INSTANCE = new PublishingProfileService();

    private final Gson gson;
    private List<PublishingProfile> profiles;

    private PublishingProfileService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadProfiles();
    }

    public static PublishingProfileService getInstance() {
        return INSTANCE;
    }

    private void loadProfiles() {
        Path path = AppPaths.dataFile(PROFILES_FILE);

        if (Files.notExists(path)) {
            profiles = new ArrayList<>();
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) {
                profiles = new ArrayList<>();
                return;
            }

            Type type = new TypeToken<List<PublishingProfile>>() {
            }.getType();
            List<PublishingProfile> loaded = gson.fromJson(json, type);
            profiles = (loaded != null) ? loaded : new ArrayList<>();
        } catch (JsonParseException e) {
            backupCorruptedFile(path);
            profiles = new ArrayList<>();
            log.warn("Invalid publishing profiles file detected and recreated. Reason: {}", e.getMessage());
        } catch (IOException e) {
            profiles = new ArrayList<>();
            log.error("Failed to load publishing profiles: {}", e.getMessage());
        }
    }

    private static void backupCorruptedFile(Path original) {
        try {
            if (Files.exists(original)) {
                Path backup = original.resolveSibling("publishing_profiles_corrupted_" + System.currentTimeMillis() + ".json");
                Files.move(original, backup, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            log.warn("Failed to backup corrupted file, will attempt to create a new one later.");
        }
    }

    private void saveProfiles() throws IOException {
        Path path = AppPaths.dataFile(PROFILES_FILE);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            gson.toJson(profiles, writer);
        }
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public PublishingProfile getProfile(String targetId) {
        return profiles.stream()
                .filter(p -> p.getTargetId().equals(targetId))
                .findFirst()
                .orElse(null);
    }

    /**
     * يحفظ ملف التعريف، أو يحذفه إن كانت كل حقوله فارغة — وإلا تراكمت ملفات تعريف
     * فارغة بلا أثر لمجرّد أن المستخدم فتح شاشة الإعدادات وأغلقها.
     */
    public void saveProfile(PublishingProfile profile) throws IOException {
        if (profile.isEmpty()) {
            deleteProfile(profile.getTargetId());
            return;
        }

        profiles.removeIf(p -> p.getTargetId().equals(profile.getTargetId()));
        profiles.add(profile);
        saveProfiles();
    }

    public void deleteProfile(String targetId) throws IOException {
        if (profiles.removeIf(p -> p.getTargetId().equals(targetId))) {
            saveProfiles();
        }
    }

    /**
     * يسوّي ملف تعريف الوجهة مع الإعدادات العامة إلى قيم محسومة.
     *
     * @param target الوجهة، أو {@code null} إذا لم تُختَر بعد (كما في نافذة
     *               المعاينة قبل اختيار مدونة) — تُستخدم الإعدادات العامة وحدها حينها
     */
    public EffectiveSettings resolve(PublishTarget target) {
        PublishingProfile profile = target == null ? null : getProfile(target.getId());
        ConfigService config = ConfigService.getInstance();

        return new EffectiveSettings(
                firstNonBlank(value(profile, PublishingProfile::getAiProvider), config.getAIProvider()),
                firstNonBlank(value(profile, PublishingProfile::getImageProvider), config.getImageProvider()),
                firstNonBlank(value(profile, PublishingProfile::getContentLanguage), config.getContentLanguage()),
                firstNonBlank(value(profile, PublishingProfile::getTitleLanguage), config.getTitleLanguage()),
                firstNonBlank(value(profile, PublishingProfile::getSlugType), config.getSlugType()),
                profile != null && profile.getPublishDelaySeconds() != null
                        ? profile.getPublishDelaySeconds()
                        : config.getPublishDelay(),
                value(profile, PublishingProfile::getDefaultCategory),
                firstNonBlank(value(profile, PublishingProfile::getPostStatus), config.getPostStatus()),
                blankToNull(value(profile, PublishingProfile::getTelegramChat)),
                blankToNull(value(profile, PublishingProfile::getFacebookPageId)));
    }

    private static String value(PublishingProfile profile, java.util.function.Function<PublishingProfile, String> getter) {
        return profile == null ? null : getter.apply(profile);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String override, String fallback) {
        return (override != null && !override.isBlank()) ? override : fallback;
    }
}
