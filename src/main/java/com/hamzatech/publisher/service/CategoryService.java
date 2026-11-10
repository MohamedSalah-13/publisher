package com.hamzatech.publisher.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.hamzatech.publisher.model.BlogCategory;
import com.hamzatech.publisher.util.AppColors;
import com.hamzatech.publisher.model.LabelMapping;
import com.hamzatech.publisher.util.AppPaths;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * خدمة لإدارة تصنيفات المدونات والأقسام
 */
@Log4j2
public class CategoryService {

    private static final String CATEGORIES_FILE = "blog_categories.json";
    private static final String LABELS_FILE = "label_mappings.json";
    private static final CategoryService INSTANCE = new CategoryService();
    private final Gson gson;
    private List<BlogCategory> categories;
    private List<LabelMapping> labelMappings;

    private CategoryService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadCategories();
        loadLabelMappings();
    }

    public static CategoryService getInstance() {
        return INSTANCE;
    }

    // =============== تصنيفات المدونات ===============

    private static void ensureParentDirExists(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static void backupCorruptedFile(Path original, String prefix) {
        try {
            if (Files.exists(original)) {
                Path backup = original.resolveSibling(prefix + "_corrupted_" + System.currentTimeMillis() + ".json");
                Files.move(original, backup, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // إذا فشل النسخ الاحتياطي، لا نمنع تشغيل التطبيق—سنحاول إنشاء ملف جديد لاحقًا.
            log.warn("Failed to backup corrupted file, will attempt to create a new one later.");
        }
    }

    /**
     * تحميل تصنيفات المدونات من الملف
     */
    private void loadCategories() {
        Path path = AppPaths.dataFile(CATEGORIES_FILE);

        if (Files.notExists(path)) {
            categories = new ArrayList<>();
            addDefaultCategories(); // سيقوم بالحفظ
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) {
                categories = new ArrayList<>();
                addDefaultCategories();
                return;
            }

            Type type = new TypeToken<List<BlogCategory>>() {
            }.getType();
            List<BlogCategory> loaded = gson.fromJson(json, type);

            categories = (loaded != null) ? loaded : new ArrayList<>();
        } catch (JsonParseException e) {
            // JSON تالف/غير صالح: خذ نسخة احتياطية ثم أعد إنشاء الافتراضي بدل انهيار التطبيق
            backupCorruptedFile(path, "blog_categories");
            categories = new ArrayList<>();
            addDefaultCategories();
            log.warn("Invalid categories file detected and recreated. Reason: {}", e.getMessage());
        } catch (IOException e) {
            categories = new ArrayList<>();
            log.error("Failed to load blog categories: {}", e.getMessage());
        }
    }

    /**
     * حفظ تصنيفات المدونات في الملف
     */
    public void saveCategories() throws IOException {
        Path path = AppPaths.dataFile(CATEGORIES_FILE);
        ensureParentDirExists(path);

        // كتابة ذرّية لتجنب ملفات ناقصة إذا توقف البرنامج أثناء الكتابة
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            gson.toJson(categories, writer);
        }
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * إضافة تصنيفات افتراضية
     */
    private void addDefaultCategories() {
        categories.add(new BlogCategory("tech", "تقنية", AppColors.ACCENT));
        categories.add(new BlogCategory("health", "صحة", AppColors.SUCCESS));
        categories.add(new BlogCategory("sports", "رياضة", AppColors.DANGER));
        categories.add(new BlogCategory("news", "أخبار", AppColors.WARNING));
        categories.add(new BlogCategory("education", "تعليم", AppColors.PURPLE));
        categories.add(new BlogCategory("other", "أخرى", AppColors.GRAY));

        try {
            saveCategories();
        } catch (IOException e) {
            log.error("Failed to save default categories: {}", e.getMessage());
        }
    }

    /**
     * الحصول على جميع التصنيفات
     */
    public List<BlogCategory> getAllCategories() {
        return new ArrayList<>(categories);
    }

    /**
     * إضافة تصنيف جديد
     */
    public void addCategory(BlogCategory category) throws IOException {
        if (getCategoryById(category.getId()) != null) {
            log.warn("Category already exists, ignoring: {}", category.getId());
            return;
        }
        categories.add(category);
        saveCategories();
    }

    /**
     * تحديث تصنيف
     */
    public void updateCategory(BlogCategory category) throws IOException {
        var existing = getBlogCategory(category.getId());
        categories.remove(existing);
        categories.add(category);
        saveCategories();
    }

    /**
     * حذف تصنيف
     */
    public void deleteCategory(String categoryId) throws IOException {
        categories.removeIf(c -> c.getId().equals(categoryId));
        saveCategories();
    }

    /**
     * الحصول على تصنيف بواسطة المعرّف
     */
    public BlogCategory getCategoryById(String id) {
        return categories.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * ربط مدونة بتصنيف
     */
    public void assignBlogToCategory(String blogId, String categoryId) throws IOException {
        var category = getBlogCategory(categoryId);
        category.addBlog(blogId);
        saveCategories();
    }

    @NotNull
    private BlogCategory getBlogCategory(String categoryId) {
        BlogCategory category = getCategoryById(categoryId);
        if (category == null) {
            log.warn("Category does not exist, ignoring: {}", categoryId);
            throw new IllegalArgumentException("التصنيف غير موجود");
        }
        return category;
    }

    // =============== تخصيص أسماء Labels ===============

    /**
     * إلغاء ربط مدونة من تصنيف
     */
    public void unassignBlogFromCategory(String blogId, String categoryId) throws IOException {
        BlogCategory category = getCategoryById(categoryId);
        if (category != null) {
            category.removeBlog(blogId);
            saveCategories();
        }
    }

    /**
     * تحميل تخصيصات الـ Labels من الملف
     */
    private void loadLabelMappings() {
        Path path = AppPaths.dataFile(LABELS_FILE);

        if (Files.notExists(path)) {
            labelMappings = new ArrayList<>();
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) {
                labelMappings = new ArrayList<>();
                return;
            }

            Type type = new TypeToken<List<LabelMapping>>() {
            }.getType();
            List<LabelMapping> loaded = gson.fromJson(json, type);

            labelMappings = (loaded != null) ? loaded : new ArrayList<>();
        } catch (JsonParseException e) {
            backupCorruptedFile(path, "label_mappings");
            labelMappings = new ArrayList<>();
            log.warn("Invalid label mappings file detected and recreated. Reason: {}", e.getMessage());

        } catch (IOException e) {
            labelMappings = new ArrayList<>();
            log.error("Failed to load label mappings: {}", e.getMessage());
        }
    }

    /**
     * حفظ تخصيصات الـ Labels في الملف
     */
    public void saveLabelMappings() throws IOException {
        Path path = AppPaths.dataFile(LABELS_FILE);
        ensureParentDirExists(path);

        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            gson.toJson(labelMappings, writer);
        }
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }


    // =============== Helpers ===============
    /**
     * الحصول على تخصيص Label
     */
    private LabelMapping getLabelMapping(String blogId, String originalLabel) {
        return labelMappings.stream()
                .filter(m -> m.getBlogId().equals(blogId) && m.getOriginalLabel().equals(originalLabel))
                .findFirst()
                .orElse(null);
    }
    /**
     * الحصول على تصنيف مدونة معينة
     */
    public BlogCategory getCategoryForBlog(String blogId) {
        return categories.stream()
                .filter(c -> c.getBlogIds().contains(blogId))
                .findFirst()
                .orElse(null);
    }
    /**
     * تخصيص اسم لـ Label
     */
    public void setLabelCustomName(String blogId, String originalLabel, String customName) throws IOException {
        LabelMapping existing = getLabelMapping(blogId, originalLabel);
        if (existing != null) {
            existing.setCustomName(customName);
        } else {
            labelMappings.add(new LabelMapping(blogId, originalLabel, customName));
        }
        saveLabelMappings();
    }

    /**
     * الحصول على الاسم المخصص لـ Label
     */
    public String getCustomLabelName(String blogId, String originalLabel) {
        LabelMapping mapping = getLabelMapping(blogId, originalLabel);
        return mapping != null && mapping.getCustomName() != null ? mapping.getCustomName() : originalLabel;
    }

    /**
     * الحصول على جميع تخصيصات Labels لمدونة معينة
     */
    public List<LabelMapping> getLabelMappingsForBlog(String blogId) {
        return labelMappings.stream()
                .filter(m -> m.getBlogId().equals(blogId))
                .toList();
    }
}