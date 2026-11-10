package com.hamzatech.publisher.service;

import com.hamzatech.publisher.model.BlogCategory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryServiceTest {

    private final CategoryService categoryService = CategoryService.getInstance();

    @Test
    void defaultCategoriesAreSeededOnFirstLoad() {
        List<BlogCategory> categories = categoryService.getAllCategories();
        assertTrue(categories.size() >= 6);
        assertNotNull(categoryService.getCategoryById("tech"));
    }

    @Test
    void addUpdateDeleteCategory_roundTrip() throws IOException {
        String id = "test-category-" + System.nanoTime();
        BlogCategory category = new BlogCategory(id, "Test", "#123456");

        try {
            categoryService.addCategory(category);
            assertNotNull(categoryService.getCategoryById(id));

            category.setName("Updated");
            categoryService.updateCategory(category);
            assertEquals("Updated", categoryService.getCategoryById(id).getName());
        } finally {
            categoryService.deleteCategory(id);
        }

        assertNull(categoryService.getCategoryById(id));
    }

    @Test
    void addCategory_ignoresDuplicateId() throws IOException {
        String id = "dup-category-" + System.nanoTime();
        try {
            categoryService.addCategory(new BlogCategory(id, "First", "#111111"));
            categoryService.addCategory(new BlogCategory(id, "Second", "#222222"));

            assertEquals("First", categoryService.getCategoryById(id).getName());
        } finally {
            categoryService.deleteCategory(id);
        }
    }

    @Test
    void assignAndUnassignBlogToCategory() throws IOException {
        String id = "assign-category-" + System.nanoTime();
        try {
            categoryService.addCategory(new BlogCategory(id, "Assign", "#333333"));

            categoryService.assignBlogToCategory("blog-1", id);
            assertEquals(id, categoryService.getCategoryForBlog("blog-1").getId());

            categoryService.unassignBlogFromCategory("blog-1", id);
            assertNull(categoryService.getCategoryForBlog("blog-1"));
        } finally {
            categoryService.deleteCategory(id);
        }
    }

    @Test
    void assignBlogToCategory_throwsForUnknownCategory() {
        assertThrows(IllegalArgumentException.class,
                () -> categoryService.assignBlogToCategory("blog-x", "no-such-category"));
    }
}
