package com.hamzatech.publisher.controller;

import com.google.api.services.blogger.model.Blog;
import com.hamzatech.publisher.model.BlogCategory;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.service.BloggerService;
import com.hamzatech.publisher.service.CategoryService;
import com.hamzatech.publisher.service.WordPressSiteService;
import com.hamzatech.publisher.util.AppColors;
import com.hamzatech.publisher.util.LocaleManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

import static com.hamzatech.publisher.controller.DashboardController.rootCauseMessage;

@Log4j2
public class CategoriesController {

    private final LocaleManager localeManager = LocaleManager.getInstance();
    @FXML
    private ListView<BlogCategory> categoriesList;
    @FXML
    private TextField txtCategoryId;
    @FXML
    private TextField txtCategoryName;
    @FXML
    private ColorPicker colorPicker;
    @FXML
    private ComboBox<BlogCategory> cmbCategoryFilter;
    @FXML
    private ComboBox<PublishTarget> cmbBlogToAssign;
    @FXML
    private ListView<String> assignedBlogsList;
    @FXML
    private Label lblStatus;
    private CategoryService categoryService;
    private BloggerService bloggerService;
    private ResourceBundle bundle;

    /** كل وجهات النشر المتاحة للإسناد: مدونات Blogger ومواقع WordPress معًا. */
    private List<PublishTarget> allTargets;

    @FXML
    public void initialize() {
        bundle = localeManager.getResourceBundle();

        try {
            this.categoryService = CategoryService.getInstance();
            this.bloggerService = BloggerService.getInstance();
        } catch (Throwable t) {
            log.error("Failed to initialize controllers", t);

            String root = rootCauseMessage(t);
            safeSetStatus(root);
            disableUi();
            return;
        }

        // تحميل التصنيفات
        loadCategories();

        // تحميل المدونات
        loadBlogs();

        categoriesList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                displayCategory(newVal);
            }
        });

        cmbCategoryFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                displayAssignedBlogs(newVal);
            }
        });

        // toString يميّز مواقع ووردبريس عن مدونات Blogger، وهو ما يحتاجه المستخدم
        // حين يحمل الاثنان الاسم نفسه
        cmbBlogToAssign.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(PublishTarget target, boolean empty) {
                super.updateItem(target, empty);
                setText(empty || target == null ? null : target.toString());
            }
        });
        cmbBlogToAssign.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(PublishTarget target, boolean empty) {
                super.updateItem(target, empty);
                setText(empty || target == null ? null : target.toString());
            }
        });
    }

    /**
     * تحميل التصنيفات
     */
    private void loadCategories() {
        List<BlogCategory> categories = categoryService.getAllCategories();
        categoriesList.getItems().setAll(categories);
        cmbCategoryFilter.getItems().setAll(categories);

        // تخصيص عرض التصنيفات
        categoriesList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(BlogCategory category, boolean empty) {
                super.updateItem(category, empty);
                if (empty || category == null) {
                    setText(null);
                    setGraphic(null);
                    setTextFill(null);
                    // الخلايا يُعاد استخدامها، فأزل الصنف وإلا ورثته صفوف فارغة
                    getStyleClass().remove("text-bold");
                } else {
                    setText(category.getName() + " (" + category.getBlogIds().size() + " " + bundle.getString("categories.blogs.count") + ")");
                    // اللون من بيانات المستخدم فلا يمكن أن يكون صنفًا ثابتًا، لكن
                    // التغميق يمكن — والتلوين هنا كما في شاشة السجلات
                    setTextFill(category.getColor() != null ? Color.web(category.getColor()) : null);
                    if (!getStyleClass().contains("text-bold")) {
                        getStyleClass().add("text-bold");
                    }
                }
            }
        });
    }

    /**
     * تحميل وجهات النشر المتاحة للإسناد.
     * <p>
     * مواقع ووردبريس محفوظة محليًا فتظهر فورًا، بينما مدونات Blogger تحتاج نداء
     * شبكة. عرضها على مرحلتين مقصود: فشل تسجيل الدخول إلى Blogger كان يترك
     * القائمة فارغة تمامًا، فلا يستطيع المستخدم إسناد مواقع ووردبريس أيضًا.
     */
    private void loadBlogs() {
        allTargets = new ArrayList<>(loadWordPressTargets());
        cmbBlogToAssign.getItems().setAll(allTargets);

        Task<List<Blog>> task = new Task<>() {
            @Override
            protected List<Blog> call() throws Exception {
                return bloggerService.getMyBlogs();
            }
        };

        task.setOnSucceeded(e -> {
            for (Blog blog : task.getValue()) {
                allTargets.add(PublishTarget.ofBlogger(blog.getId(), blog.getName(), blog.getUrl()));
            }
            cmbBlogToAssign.getItems().setAll(allTargets);
            refreshAssignedBlogs();
        });

        task.setOnFailed(e -> lblStatus.setText(bundle.getString("categories.error.load.blogs")));

        new Thread(task).start();
    }

    private List<PublishTarget> loadWordPressTargets() {
        return WordPressSiteService.getInstance().getAllSites().stream()
                .map(PublishTarget::ofWordPress)
                .toList();
    }

    /** يعيد رسم قائمة المسندة بعد وصول مدونات Blogger. */
    private void refreshAssignedBlogs() {
        BlogCategory selected = cmbCategoryFilter.getSelectionModel().getSelectedItem();
        if (selected != null) {
            displayAssignedBlogs(selected);
        }
    }

    /**
     * عرض تصنيف في الحقول
     */
    private void displayCategory(BlogCategory category) {
        txtCategoryId.setText(category.getId());
        txtCategoryName.setText(category.getName());

        if (category.getColor() != null) {
            try {
                colorPicker.setValue(Color.web(category.getColor()));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * عرض المدونات المرتبطة بتصنيف
     */
    private void displayAssignedBlogs(BlogCategory category) {
        if (allTargets == null) {
            assignedBlogsList.getItems().clear();
            return;
        }

        List<String> names = category.getBlogIds().stream()
                .map(this::targetNameById)
                .filter(Objects::nonNull)
                .toList();

        assignedBlogsList.getItems().setAll(names);
    }

    /**
     * اسم الوجهة بمعرّفها، أو {@code null} إذا لم تعد موجودة — مدونة حُذفت أو
     * موقع ووردبريس أُزيل تبقى معرّفاته في التصنيف ولا اسم لها.
     */
    private String targetNameById(String targetId) {
        if (allTargets == null) return null;
        return allTargets.stream()
                .filter(t -> t.getId().equals(targetId))
                .map(PublishTarget::toString)
                .findFirst()
                .orElse(null);
    }

    /**
     * إضافة تصنيف جديد
     */
    @FXML
    public void handleAddCategory() {
        String id = txtCategoryId.getText().trim();
        String name = txtCategoryName.getText().trim();

        if (id.isEmpty() || name.isEmpty()) {
            showError(bundle.getString("categories.error.fill.fields"));
            return;
        }

        String color = toHexString(colorPicker.getValue());
        BlogCategory category = new BlogCategory(id, name, color);

        try {
            categoryService.addCategory(category);
            loadCategories();
            clearFields();
            lblStatus.setText(bundle.getString("categories.success.added"));
        } catch (IOException e) {
            showError(java.text.MessageFormat.format(
                bundle.getString("categories.error.failed.add"),
                e.getMessage()
            ));
        }
    }

    /**
     * تحديث تصنيف
     */
    @FXML
    public void handleUpdateCategory() {
        BlogCategory selected = categoriesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError(bundle.getString("categories.error.select.category"));
            return;
        }

        String newName = txtCategoryName.getText().trim();
        if (newName.isEmpty()) {
            showError(bundle.getString("categories.error.enter.name"));
            return;
        }

        selected.setName(newName);
        selected.setColor(toHexString(colorPicker.getValue()));

        try {
            categoryService.updateCategory(selected);
            loadCategories();
            lblStatus.setText(bundle.getString("categories.success.updated"));
        } catch (IOException e) {
            showError(java.text.MessageFormat.format(
                bundle.getString("categories.error.failed.update"),
                e.getMessage()
            ));
        }
    }

    /**
     * حذف تصنيف
     */
    @FXML
    public void handleDeleteCategory() {
        BlogCategory selected = categoriesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError(bundle.getString("categories.error.select.category"));
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(bundle.getString("categories.confirm.delete.title"));
        alert.setHeaderText(bundle.getString("categories.confirm.delete.message"));
        alert.setContentText(selected.getName());

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                categoryService.deleteCategory(selected.getId());
                loadCategories();
                clearFields();
                lblStatus.setText(bundle.getString("categories.success.deleted"));
            } catch (IOException e) {
                showError(java.text.MessageFormat.format(
                    bundle.getString("categories.error.failed.delete"),
                    e.getMessage()
                ));
            }
        }
    }

    /**
     * ربط مدونة بتصنيف
     */
    @FXML
    public void handleAssignBlog() {
        BlogCategory category = cmbCategoryFilter.getSelectionModel().getSelectedItem();
        PublishTarget target = cmbBlogToAssign.getSelectionModel().getSelectedItem();

        if (category == null || target == null) {
            showError(bundle.getString("categories.error.select.both"));
            return;
        }

        try {
            categoryService.assignBlogToCategory(target.getId(), category.getId());
            loadCategories();
            displayAssignedBlogs(category);
            lblStatus.setText(bundle.getString("categories.success.linked"));
        } catch (IOException e) {
            showError(java.text.MessageFormat.format(
                bundle.getString("categories.error.failed.link"),
                e.getMessage()
            ));
        }
    }

    /**
     * إلغاء ربط مدونة من تصنيف
     */
    @FXML
    public void handleUnassignBlog() {
        BlogCategory category = cmbCategoryFilter.getSelectionModel().getSelectedItem();
        PublishTarget target = cmbBlogToAssign.getSelectionModel().getSelectedItem();

        if (category == null || target == null) {
            showError(bundle.getString("categories.error.select.both"));
            return;
        }

        try {
            categoryService.unassignBlogFromCategory(target.getId(), category.getId());
            loadCategories();
            displayAssignedBlogs(category);
            lblStatus.setText(bundle.getString("categories.success.unlinked"));
        } catch (IOException e) {
            showError(java.text.MessageFormat.format(
                bundle.getString("categories.error.failed.unlink"),
                e.getMessage()
            ));
        }
    }

    /**
     * إغلاق النافذة
     */
    @FXML
    public void handleClose() {
        WorkspaceTabs.close(lblStatus);
    }

    /**
     * مسح الحقول
     */
    private void clearFields() {
        txtCategoryId.clear();
        txtCategoryName.clear();
        colorPicker.setValue(Color.web(AppColors.ACCENT));
    }

    /**
     * عرض رسالة خطأ
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(bundle.getString("categories.error.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        log.error(message);
    }

    /**
     * تحويل لون إلى صيغة Hex
     */
    private String toHexString(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private void disableUi() {
        if (categoriesList != null) categoriesList.setDisable(true);
        if (cmbCategoryFilter != null) cmbCategoryFilter.setDisable(true);
        if (cmbBlogToAssign != null) cmbBlogToAssign.setDisable(true);
        if (txtCategoryId != null) txtCategoryId.setDisable(true);
        if (txtCategoryName != null) txtCategoryName.setDisable(true);
        if (colorPicker != null) colorPicker.setDisable(true);
        if (assignedBlogsList != null) assignedBlogsList.setDisable(true);
    }

    private void safeSetStatus(String message) {
        if (lblStatus != null) {
            lblStatus.setText(message);
        } else {
            log.error(message);
        }
    }
}
