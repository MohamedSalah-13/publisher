package com.hamzatech.publisher.controller;

import com.google.api.services.blogger.model.Blog;
import com.hamzatech.publisher.model.BatchPublishResult;
import com.hamzatech.publisher.model.BatchStatus;
import com.hamzatech.publisher.model.BlogCategory;
import com.hamzatech.publisher.model.PublishLog;
import com.hamzatech.publisher.model.PublishPlatform;
import com.hamzatech.publisher.model.PublishJob;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.model.QueueItemStatus;
import com.hamzatech.publisher.service.*;
import com.hamzatech.publisher.util.AppWindow;
import com.hamzatech.publisher.util.LocaleManager;
import com.hamzatech.publisher.util.ThemeManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@Log4j2
public class DashboardController {

    private static final String CATEGORIES_FXML = "/com/hamzatech/publisher/view/categories.fxml";
    private static final String SETTINGS_FXML = "/com/hamzatech/publisher/view/settings.fxml";
    private static final String PUBLISH_LOGS_FXML = "/com/hamzatech/publisher/view/publish_logs.fxml";
    private static final String WORDPRESS_SITES_FXML = "/com/hamzatech/publisher/view/wordpress_sites.fxml";
    private static final String QUEUE_FXML = "/com/hamzatech/publisher/view/publish_queue.fxml";
    private final LocaleManager localeManager = LocaleManager.getInstance();
    @FXML
    private Label lblUserEmail;
    @FXML private OverviewController overviewController;
    @FXML private TabPane workspaceTabs;
    @FXML private Tab publishingTab;
    @FXML
    private ComboBox<PublishTarget> cmbBlogs;
    @FXML
    private ComboBox<String> cmbCategoryFilter;
    @FXML
    private ComboBox<String> cmbLanguage;
    @FXML
    private Button btnThemeToggle;
    @FXML
    private FlowPane categoriesPane;
    @FXML
    private Label lblFilePath;
    @FXML
    private Button btnStart;
    @FXML
    private Button btnStop;
    @FXML
    private Button btnDeleteFile;
    @FXML
    private Button btnPreview;
    @FXML
    private Button btnBlogProfile;
    @FXML
    private Button btnQueue;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label lblStatus;
    @FXML
    private CheckBox chkSchedule;
    @FXML
    private DatePicker dpScheduleDate;
    @FXML
    private Spinner<Integer> spnScheduleHour;
    @FXML
    private Spinner<Integer> spnScheduleMinute;
    @FXML
    private Label lblScheduleStatus;
    @FXML
    private Button btnCancelSchedule;
    // تعريف السيرفس (تهيئة كسولة داخل initialize لتجنب crash أثناء إنشاء الـController)
    private ExcelService excelService;
    private BloggerService bloggerService;
    // ✅ اجعل CategoryService دائمًا غير null (Singleton آمن للاستخدام هنا)
    private CategoryService categoryService;
    private PublishLogService publishLogService;
    private ConfigService configService;
    private ResourceBundle bundle;
    private File selectedExcelFile;
    private String selectedCategory = null;
    private List<PublishTarget> allTargets = new ArrayList<>();
    private PublishScheduleControls schedule;
    private WorkspaceTabs workspace;

    public static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        if (msg == null || msg.isBlank()) msg = cur.getClass().getSimpleName();
        return msg;
    }

    /**
     * يحوّل استثناء تقني إلى رسالة مفهومة للمستخدم بدل عرض اسم الكلاس/رسالة الشبكة الخام.
     */
    private String friendlyErrorMessage(Throwable t) {
        if (t == null) return bundle.getString("dashboard.error.unknown");

        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        if (root instanceof java.net.UnknownHostException
                || root instanceof java.net.ConnectException
                || root instanceof java.net.SocketTimeoutException) {
            return bundle.getString("dashboard.error.network");
        }
        if (root instanceof java.security.GeneralSecurityException) {
            return bundle.getString("dashboard.error.auth");
        }
        if (root instanceof IllegalStateException && root.getMessage() != null && !root.getMessage().isBlank()) {
            // رسائل تحقق داخلية (مثل "لم يتم اختيار مدونة") مأخوذة أصلاً من bundle، فهي مفهومة كما هي
            return root.getMessage();
        }

        return bundle.getString("dashboard.error.unknown") + ": " + rootCauseMessage(root);
    }

    @FXML
    public void initialize() {
        log.info("Initializing DashboardController");
        bundle = localeManager.getResourceBundle();

        // Setup language ComboBox
        cmbLanguage.getItems().addAll(
                bundle.getString("language.arabic"),
                bundle.getString("language.english")
        );
        cmbLanguage.setValue(localeManager.isRTL() ?
                bundle.getString("language.arabic") : bundle.getString("language.english"));

        cmbLanguage.setOnAction(e -> handleLanguageChange());

        updateThemeIcon();
        btnThemeToggle.setOnAction(e -> handleThemeToggle());

        schedule = new PublishScheduleControls(chkSchedule, dpScheduleDate, spnScheduleHour,
                spnScheduleMinute, lblScheduleStatus, btnCancelSchedule, this::startPublishing);
        overviewController.setActions(() -> workspaceTabs.getSelectionModel().select(publishingTab),
                this::handlePublishLogs, this::handleQueue);
        workspace = new WorkspaceTabs(workspaceTabs);
        workspaceTabs.getSelectionModel().selectedIndexProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue.intValue() == 0) overviewController.refresh();
        });

        try {
            log.debug("Initializing services");
            this.excelService = new ExcelService();
            this.bloggerService = BloggerService.getInstance();
            this.categoryService = CategoryService.getInstance();
            this.publishLogService = PublishLogService.getInstance();
            this.configService = ConfigService.getInstance();
            log.info("Services initialized successfully");
        } catch (Exception t) {
            log.error("Failed to initialize services", t);
            safeSetStatus(bundle.getString("dashboard.error.init.services") + ": " + rootCauseMessage(t));

            disableUiOnInitFailure();
            return;
        }

        loadCategoryFilter();
        loadBlogs();
        loadUserEmail();
        updateQueueButton();

        cmbBlogs.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            btnBlogProfile.setDisable(newVal == null);
            if (newVal != null) {
                loadCategories(newVal);
            }
        });

        cmbCategoryFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> filterBlogsByCategory());
    }

    private void handleThemeToggle() {
        ThemeManager.getInstance().toggle();
        updateThemeIcon();
    }

    private void updateThemeIcon() {
        boolean dark = ThemeManager.getInstance().isDarkMode();
        btnThemeToggle.setGraphic(new FontIcon(dark ? "fth-sun" : "fth-moon"));
    }

    private void handleLanguageChange() {
        String selected = cmbLanguage.getValue();
        ResourceBundle currentBundle = localeManager.getResourceBundle();

        if (selected.equals(currentBundle.getString("language.arabic"))) {
            localeManager.setLocale(LocaleManager.ARABIC);
        } else {
            localeManager.setLocale(LocaleManager.ENGLISH);
        }

        // Reload the current scene with new locale
        reloadScene();
    }

    private void reloadScene() {
        try {
            ResourceBundle newBundle = localeManager.getResourceBundle();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/hamzatech/publisher/view/dashboard.fxml"),
                    newBundle
            );
            Parent root = loader.load();

            Scene scene = AppWindow.createScene(root);
            localeManager.applyOrientation(scene);

            Stage stage = (Stage) lblStatus.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle(newBundle.getString("dashboard.title"));
        } catch (Exception e) {
            log.error("Failed to reload scene", e);
        }
    }

    private void loadCategoryFilter() {
        try {
            List<BlogCategory> categories = categoryService.getAllCategories();
            cmbCategoryFilter.getItems().clear();
            cmbCategoryFilter.getItems().add(bundle.getString("dashboard.category.all"));

            for (BlogCategory category : categories) {
                cmbCategoryFilter.getItems().add(category.getName());
            }

            cmbCategoryFilter.getSelectionModel().selectFirst();
        } catch (Exception ex) {
            cmbCategoryFilter.getItems().setAll(bundle.getString("dashboard.category.all"));
            cmbCategoryFilter.getSelectionModel().selectFirst();
            lblStatus.setText(bundle.getString("dashboard.status.ready"));
        }
    }

    private void disableUiOnInitFailure() {
        if (btnStart != null) btnStart.setDisable(true);
        if (cmbBlogs != null) cmbBlogs.setDisable(true);
        if (cmbCategoryFilter != null) cmbCategoryFilter.setDisable(true);
    }

    private void safeSetStatus(String message) {
        if (lblStatus != null) {
            lblStatus.setText(message);
        } else {
            System.err.println(message);
        }
    }

    private void filterBlogsByCategory() {
        String selectedFilter = cmbCategoryFilter.getSelectionModel().getSelectedItem();
        String allCategoriesText = bundle.getString("dashboard.category.all");

        if (selectedFilter == null || allCategoriesText.equals(selectedFilter)) {
            cmbBlogs.getItems().setAll(allTargets);
        } else {
            BlogCategory category = categoryService.getAllCategories().stream()
                    .filter(c -> c.getName().equals(selectedFilter))
                    .findFirst()
                    .orElse(null);

            if (category != null) {
                List<PublishTarget> filteredTargets = allTargets.stream()
                        .filter(target -> category.getBlogIds().contains(target.getId()))
                        .toList();
                cmbBlogs.getItems().setAll(filteredTargets);
            }
        }

        if (!cmbBlogs.getItems().isEmpty()) {
//            cmbBlogs.getSelectionModel().selectFirst();
        }
    }

    private void loadUserEmail() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return bloggerService.getUserEmail();
            }
        };

        task.setOnSucceeded(e -> {
            String email = task.getValue();
            if (email != null && !email.isEmpty()) {
                lblUserEmail.setText(email);
            }
        });

        task.setOnFailed(e -> lblUserEmail.setText(bundle.getString("dashboard.status.not.connected")));
        Thread th = new Thread(task, "load-user-email");
        th.setDaemon(true);
        th.start();
    }

    private void loadBlogs() {
        Task<List<Blog>> task = new Task<>() {
            @Override
            protected List<Blog> call() throws Exception {
                return bloggerService.getMyBlogs();
            }
        };

        task.setOnSucceeded(e -> {
            List<PublishTarget> targets = new ArrayList<>();
            for (Blog blog : task.getValue()) {
                targets.add(PublishTarget.ofBlogger(blog.getId(), blog.getName(), blog.getUrl()));
            }
            targets.addAll(loadWordPressTargets());
            allTargets = targets;
            filterBlogsByCategory();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            lblStatus.setText(bundle.getString("dashboard.status.failed.loading.blogs") + ": " + friendlyErrorMessage(ex));
            if (ex != null) log.error("Failed to load blogs", ex);

            // حتى لو فشل تحميل مدونات Blogger (اتصال/صلاحيات)، أظهر مواقع WordPress المحفوظة على الأقل
            allTargets = new ArrayList<>(loadWordPressTargets());
            filterBlogsByCategory();
        });

        Thread th = new Thread(task, "load-blogs");
        th.setDaemon(true);
        th.start();
    }

    private List<PublishTarget> loadWordPressTargets() {
        return WordPressSiteService.getInstance().getAllSites().stream()
                .map(PublishTarget::ofWordPress)
                .toList();
    }

    // دالة لتوليد كروت الأقسام
    private void loadCategories(PublishTarget target) {
        categoriesPane.getChildren().clear();
        selectedCategory = null;

        lblStatus.setText(bundle.getString("dashboard.status.loading.categories"));

        // المنصتان تسمّيان الفكرة تسميتين مختلفتين — وسوم في Blogger وتصنيفات في
        // ووردبريس — لكن الاختيار في الشاشة واحد، فيكفي أن يختلف مصدر القائمة
        Task<java.util.Map<String, Integer>> task = new Task<>() {
            @Override
            protected java.util.Map<String, Integer> call() throws Exception {
                if (target.getPlatform() == PublishPlatform.WORDPRESS) {
                    return new WordPressService().listCategories(
                            target.getUrl(), target.getWpUsername(), target.getWpApplicationPassword());
                }
                return bloggerService.getLabelsWithCount(target.getId());
            }
        };

        task.setOnSucceeded(e -> {
            java.util.Map<String, Integer> labelCountMap = task.getValue();

            if (labelCountMap.isEmpty()) {
                lblStatus.setText(bundle.getString("dashboard.status.no.categories"));
            } else {
                // ملف تعريف الوجهة قد يحمل تصنيفًا افتراضيًا يُختار تلقائيًا هنا
                String defaultCategory = PublishingProfileService.getInstance().resolve(target).defaultCategory();

                for (java.util.Map.Entry<String, Integer> entry : labelCountMap.entrySet()) {
                    VBox card = createCategoryCard(entry.getKey(), entry.getValue());
                    categoriesPane.getChildren().add(card);
                    if (entry.getKey().equals(defaultCategory)) {
                        selectCategoryCard(card, entry.getKey());
                    }
                }
                lblStatus.setText(java.text.MessageFormat.format(
                        bundle.getString("dashboard.status.categories.loaded"),
                        labelCountMap.size(),
                        target.getName()
                ));
            }
            checkReadyToStart();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            lblStatus.setText(java.text.MessageFormat.format(
                    bundle.getString("dashboard.status.failed.loading.categories"),
                    friendlyErrorMessage(ex)
            ));
            // بدون هذا يبقى زر البدء على حالته السابقة، وقد تكون من وجهة أخرى
            checkReadyToStart();
            if (ex != null) log.error("Failed to load categories", ex);
        });

        new Thread(task).start();
    }

    // دالة إنشاء تصميم الكرت برمجياً
    private VBox createCategoryCard(String labelName, int postCount) {
        VBox card = new VBox(5);
        card.getStyleClass().add("category-card");
        card.setAlignment(Pos.CENTER);

        Label nameLbl = new Label(labelName);
        nameLbl.getStyleClass().add("category-label");

        // عرض عدد المقالات
        Label countLbl = new Label(postCount + bundle.getString("dashboard.article"));
        countLbl.getStyleClass().add("category-count");

        card.getChildren().addAll(nameLbl, countLbl);
        card.setOnMouseClicked(e -> selectCategoryCard(card, labelName));

        return card;
    }

    /**
     * يُستدعى من نقرة المستخدم، ومن التحديد التلقائي للتصنيف الافتراضي في ملف
     * تعريف الوجهة — نفس المنطق في الحالتين.
     */
    private void selectCategoryCard(VBox card, String labelName) {
        for (Node node : categoriesPane.getChildren()) {
            node.getStyleClass().remove("category-card-selected");
        }
        card.getStyleClass().add("category-card-selected");
        this.selectedCategory = labelName;
        checkReadyToStart();
    }

    @FXML
    public void handleUploadExcel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showOpenDialog(btnStart.getScene().getWindow());

        if (file != null) {
            useExcelFile(file);
        }
    }

    /**
     * يختار ملف العناوين للنشر — مسار واحد للملف المرفوع وللملف الذي حفظته نافذة
     * توليد العناوين، فلا يختلف ما يليه في شيء.
     */
    private void useExcelFile(File file) {
        this.selectedExcelFile = file;
        lblFilePath.setText(file.getName());
        btnDeleteFile.setVisible(true);
        btnPreview.setDisable(false);
        checkReadyToStart();
    }

    @FXML
    public void handleGenerateTitles() {
        PublishTarget target = cmbBlogs.getSelectionModel().getSelectedItem();
        new TitleGeneratorDialog(btnStart.getScene().getWindow(), target, file -> {
            useExcelFile(file);
            lblStatus.setText(java.text.MessageFormat.format(
                    bundle.getString("titles.file.ready"), file.getName()));
        }, this::friendlyErrorMessage).show();
    }

    @FXML
    public void handleDeleteFile() {
        this.selectedExcelFile = null;
        lblFilePath.setText(bundle.getString("dashboard.file.none"));
        btnDeleteFile.setVisible(false);
        btnPreview.setDisable(true);
        checkReadyToStart();
        log.info("Excel file removed");
    }

    @FXML
    public void handlePreview() {
        if (selectedExcelFile == null) {
            lblStatus.setText(bundle.getString("dashboard.preview.empty.file"));
            return;
        }

        Task<List<String>> readTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return excelService.readTitles(selectedExcelFile);
            }
        };

        readTask.setOnSucceeded(e -> showPreviewDialog(readTask.getValue()));
        readTask.setOnFailed(e -> {
            log.error("Failed to read titles for preview", readTask.getException());
            lblStatus.setText(friendlyErrorMessage(readTask.getException()));
        });

        Thread th = new Thread(readTask, "preview-read-excel");
        th.setDaemon(true);
        th.start();
    }

    private void showPreviewDialog(List<String> titles) {
        if (titles == null || titles.isEmpty()) {
            lblStatus.setText(bundle.getString("dashboard.preview.empty.file"));
            return;
        }
        new ArticlePreviewDialog(titles, cmbBlogs.getSelectionModel().getSelectedItem(),
                this::friendlyErrorMessage).show();
    }

    @FXML
    public void handleEditProfile() {
        PublishTarget target = cmbBlogs.getSelectionModel().getSelectedItem();
        if (target == null) {
            return;
        }
        // التصنيفات المعروضة حاليًا للوجهة نفسها، ليختار منها التصنيف الافتراضي
        List<String> categories = new ArrayList<>();
        for (Node node : categoriesPane.getChildren()) {
            if (node instanceof VBox card && !card.getChildren().isEmpty()
                    && card.getChildren().get(0) instanceof Label nameLbl) {
                categories.add(nameLbl.getText());
            }
        }
        new PublishingProfileDialog(target, categories, message -> {
            lblStatus.setText(message);
            loadCategories(target);
        }, this::friendlyErrorMessage).show();
    }

    private void checkReadyToStart() {
        // تفعيل زر البدء فقط إذا تم اختيار ملف ووجهة نشر.
        // التصنيف مطلوب لـ Blogger ويبقى اختياريًا لووردبريس رغم أن اللوحة صارت
        // تعرض تصنيفاته: ووردبريس ينشر بلا تصنيف إلى Uncategorized، وإلزامه كان
        // سيمنع النشر على موقع لا تصنيفات فيه بعد.
        PublishTarget target = cmbBlogs.getSelectionModel().getSelectedItem();
        boolean categoryOk = target != null
                && (target.getPlatform() == PublishPlatform.WORDPRESS || selectedCategory != null);
        boolean ready = selectedExcelFile != null && categoryOk;
        btnStart.setDisable(!ready);
    }

    @FXML
    public void handleStart() {
        if (selectedExcelFile == null) {
            log.warn("Start button clicked but no Excel file selected");
            return;
        }

        if (schedule.isRequested()) {
            LocalDateTime scheduledAt = schedule.scheduledTime();
            if (scheduledAt == null || !scheduledAt.isAfter(LocalDateTime.now())) {
                lblStatus.setText(bundle.getString("dashboard.schedule.error.past"));
                return;
            }
            schedule.schedule(scheduledAt);
            btnStart.setDisable(true);
            return;
        }

        startPublishing();
    }

    private void startPublishing() {
        // كل ما يأتي من الواجهة يُلتقط هنا، على خيط الواجهة.
        // قراءة cmbBlogs من داخل Task.call() كانت وصولاً إلى JavaFX من خيط خلفي.
        final PublishTarget target = cmbBlogs.getSelectionModel().getSelectedItem();
        final String category = selectedCategory;
        final File excelFile = selectedExcelFile;

        if (target == null) {
            log.error("No blog selected");
            lblStatus.setText(bundle.getString("dashboard.error.no.blog.selected"));
            return;
        }

        log.info("Starting auto-publishing process for file: {}", excelFile.getName());
        runBatch(target, category, true, "dashboard.status.reading.excel",
                (service, delaySeconds, listener, cancelled) -> {
                    List<String> titles = excelService.readTitles(excelFile);
                    log.info("Publishing {} title(s) to: {}", titles == null ? 0 : titles.size(), target.getName());
                    return service.publishAll(titles, target, category, delaySeconds, listener, cancelled);
                });
    }

    /**
     * إعادة محاولة المقالات الفاشلة وحدها، بنفس الوجهة والتصنيف.
     * لا حاجة لإعادة بناء الوجهة من السجل كما في شاشة السجلات، فهي معروفة هنا.
     */
    private void retryFailed(List<String> titles, PublishTarget target, String category) {
        log.info("Retrying {} failed article(s)", titles.size());
        runBatch(target, category, false, "dashboard.status.retrying",
                (service, delaySeconds, listener, cancelled) ->
                        service.publishAll(titles, target, category, delaySeconds, listener, cancelled));
    }

    /**
     * يستأنف دفعة تُوقّفت من قبل — أُغلق التطبيق أثناءها، أو ضُغط زر الإيقاف —
     * من طابور {@code PublishQueueService}. الوجهة تُعاد بناؤها من هوية الدفعة
     * المخزَّنة لأن اختيار اللوحة الحالي قد يكون تغيّر منذ ذلك الحين.
     */
    private void resumeQueuedJob(PublishJob job) {
        PublishTarget target = PublishTargetResolver.resolve(job.getTargetId(), job.getTargetName());
        log.info("Resuming queued batch for: {} ({} pending)", target.getName(),
                job.countByStatus(QueueItemStatus.PENDING));
        runBatch(target, job.getCategory(), false, "dashboard.status.resuming",
                (service, delaySeconds, listener, cancelled) ->
                        service.resumeJob(job, target, delaySeconds, listener, cancelled));
    }

    /** ما يفعله {@link #runBatch} فعليًا: دفعة جديدة، إعادة محاولة، أو استئناف. */
    @FunctionalInterface
    private interface BatchRunner {
        BatchPublishResult run(BatchPublishingService service, int delaySeconds,
                                BatchPublishingService.ProgressListener listener,
                                java.util.function.BooleanSupplier cancelled) throws Exception;
    }

    /**
     * يشغّل دفعة نشر ويعرض حصيلتها. طريقة التشغيل نفسها مُمرَّرة حتى يخدم البدء
     * من ملف Excel، وإعادة محاولة الفاشل، واستئناف دفعة من الطابور، معًا دون
     * تكرار منطق التقدّم والإيقاف والتقرير.
     *
     * @param clearFileOnSuccess إفراغ الملف المختار بعد نجاح الدفعة (لا يُفعل إلا عند البدء من ملف)
     * @param preparingKey       مفتاح الرسالة المعروضة قبل بدء أول مقال
     */
    private void runBatch(PublishTarget target, String category, boolean clearFileOnSuccess,
                           String preparingKey, BatchRunner runner) {
        schedule.stopCountdown();
        // ملف تعريف الوجهة قد يغيّر التأخير الافتراضي
        final int delaySeconds = PublishingProfileService.getInstance().resolve(target).publishDelaySeconds();

        // عند الضغط على إيقاف تنتقل المهمة إلى حالة CANCELLED وتُهمَل قيمتها المرتجعة،
        // فتضيع الحصيلة. نحتفظ بها هنا لنعرضها في كلتا الحالتين.
        final java.util.concurrent.atomic.AtomicReference<BatchPublishResult> outcome =
                new java.util.concurrent.atomic.AtomicReference<>();

        Task<BatchPublishResult> task = new Task<>() {
            @Override
            protected BatchPublishResult call() throws Exception {
                updateMessage(bundle.getString(preparingKey));

                BatchPublishResult result = runner.run(BatchPublishingService.withSharedServices(), delaySeconds,
                        new BatchPublishingService.ProgressListener() {
                            @Override
                            public void onArticleStarted(int articleNumber, int total, String title) {
                                Platform.runLater(overviewController::refresh);
                                updateMessage(java.text.MessageFormat.format(
                                        bundle.getString("dashboard.status.writing.article"),
                                        articleNumber, total, title));
                            }

                            @Override
                            public void onStage(int articleNumber, int total, ArticlePublishingService.Stage stage) {
                                String key = stageMessageKey(stage);
                                if (key != null) {
                                    updateMessage(java.text.MessageFormat.format(
                                            bundle.getString(key), articleNumber, total));
                                }
                            }

                            @Override
                            public void onArticleFinished(int articleNumber, int total, PublishLog result) {
                                updateProgress(articleNumber, total);
                                Platform.runLater(overviewController::refresh);
                            }

                            @Override
                            public void onArticleSkipped(int articleNumber, int total, String title) {
                                updateMessage(java.text.MessageFormat.format(
                                        bundle.getString("dashboard.status.skipping"),
                                        articleNumber, total, title));
                                updateProgress(articleNumber, total);
                                Platform.runLater(overviewController::refresh);
                            }

                            @Override
                            public void onWaiting(int secondsRemaining) throws InterruptedException {
                                updateMessage(java.text.MessageFormat.format(
                                        bundle.getString("dashboard.status.waiting"), secondsRemaining));
                                Thread.sleep(1000L);
                            }
                        },
                        this::isCancelled);
                outcome.set(result);
                return result;
            }
        };

        lblStatus.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setVisible(true);
        btnStart.setDisable(true);
        showStopButton(task);

        task.setOnSucceeded(e -> {
            finishPublishing();
            BatchPublishResult result = task.getValue();
            reportOutcome(result, target, category,
                    clearFileOnSuccess && result.status() == BatchStatus.SUCCESS);

            loadCategories(cmbBlogs.getValue());
            updateQueueButton();
        });

        task.setOnFailed(e -> {
            finishPublishing();
            btnStart.setDisable(false);
            Throwable ex = task.getException();
            lblStatus.setText(bundle.getString("dashboard.status.failed.execution") + ": " + friendlyErrorMessage(ex));
            if (ex != null) log.error("Failed to execute auto-publishing process", ex);
            updateQueueButton();
        });

        task.setOnCancelled(e -> {
            finishPublishing();
            btnStart.setDisable(false);
            updateQueueButton();
            BatchPublishResult result = outcome.get();
            if (result == null) {
                // أُوقفت قبل أن تبدأ الدفعة فعلاً (مثلاً أثناء قراءة ملف Excel)
                lblStatus.setText(bundle.getString("dashboard.status.cancelled"));
                return;
            }
            reportOutcome(result, target, category, false);
        });

        Thread t = new Thread(task, "start-pipeline");
        t.setDaemon(true);
        t.start();
    }

    private void reportOutcome(BatchPublishResult result, PublishTarget target, String category,
                                boolean clearFile) {
        lblStatus.setText(summarise(result));
        if (clearFile) {
            clearSelectedFile();
        } else {
            // نفس قاعدة التحقق المستخدمة في الشاشة، لا شرط مبسّط يتجاهل التصنيف
            checkReadyToStart();
        }
        if (!result.failed().isEmpty()) {
            showFailureReport(result, target, category);
        }
    }

    /**
     * يظهر زر الإيقاف طوال الدفعة ويخفيه بعدها.
     */
    private void showStopButton(Task<?> task) {
        btnStop.setVisible(true);
        btnStop.setManaged(true);
        btnStop.setDisable(false);
        btnStop.setOnAction(e -> {
            btnStop.setDisable(true);
            lblStatus.textProperty().unbind();
            lblStatus.setText(bundle.getString("dashboard.status.stopping"));
            task.cancel();
        });
    }

    private void finishPublishing() {
        lblStatus.textProperty().unbind();
        progressBar.progressProperty().unbind();
        btnStop.setVisible(false);
        btnStop.setManaged(false);
        schedule.setDisabled(false);
    }

    private void clearSelectedFile() {
        selectedExcelFile = null;
        lblFilePath.setText(bundle.getString("dashboard.file.none"));
        btnDeleteFile.setVisible(false);
        checkReadyToStart();
    }

    /**
     * رسالة تقول ما حدث فعلاً بدل "اكتملت بنجاح" الثابتة.
     */
    private String summarise(BatchPublishResult result) {
        String summary = switch (result.status()) {
            case NOTHING_TO_PUBLISH -> bundle.getString("dashboard.status.nothing.to.publish");
            case ALL_SKIPPED -> java.text.MessageFormat.format(
                    bundle.getString("dashboard.status.batch.all.skipped"), result.skipped().size());
            case SUCCESS -> java.text.MessageFormat.format(
                    bundle.getString("dashboard.status.batch.success"), result.succeeded().size());
            case PARTIAL_SUCCESS -> java.text.MessageFormat.format(
                    bundle.getString("dashboard.status.batch.partial"),
                    result.succeeded().size(), result.failed().size());
            case FAILED -> java.text.MessageFormat.format(
                    bundle.getString("dashboard.status.batch.failed"), result.failed().size());
            case CANCELLED -> java.text.MessageFormat.format(
                    bundle.getString("dashboard.status.batch.cancelled"),
                    result.succeeded().size(), result.failed().size(), result.notAttempted());
        };

        // التخطّي يُذكر مع أي حصيلة، وإلا بدت الأعداد ناقصة بلا تفسير
        boolean alreadyCounted = result.status() == BatchStatus.ALL_SKIPPED
                || result.status() == BatchStatus.NOTHING_TO_PUBLISH;
        if (!result.skipped().isEmpty() && !alreadyCounted) {
            summary += java.text.MessageFormat.format(
                    bundle.getString("dashboard.status.batch.skipped.suffix"), result.skipped().size());
        }
        return summary;
    }

    /**
     * يعرض أسباب فشل المقالات بدل إخفائها في ملف السجل، ويعرض إعادة المحاولة
     * على الفاشل وحده — لا على الدفعة كاملة، حتى لا يُنشر الناجح مرتين.
     */
    private void showFailureReport(BatchPublishResult result, PublishTarget target, String category) {
        StringBuilder details = new StringBuilder();
        for (PublishLog failure : result.failed()) {
            details.append("\u2022 ").append(failure.getTitle()).append('\n')
                    .append("   ").append(failure.getErrorMessage() == null
                            ? bundle.getString("dashboard.status.unknown.error")
                            : failure.getErrorMessage())
                    .append("\n\n");
        }

        ButtonType retry = new ButtonType(
                java.text.MessageFormat.format(bundle.getString("dashboard.batch.retry.button"),
                        result.failed().size()),
                ButtonBar.ButtonData.OK_DONE);

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(bundle.getString("dashboard.batch.report.title"));
        alert.setHeaderText(summarise(result));
        alert.getButtonTypes().setAll(retry, ButtonType.CLOSE);

        TextArea area = new TextArea(details.toString().trim());
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(Math.min(16, Math.max(4, result.failed().size() * 3)));
        alert.getDialogPane().setContent(area);
        alert.getDialogPane().setPrefWidth(520);

        if (alert.showAndWait().orElse(ButtonType.CLOSE) == retry) {
            retryFailed(result.failedTitles(), target, category);
        }
    }

    @FXML
    public void handleCancelSchedule() {
        schedule.cancel();
        checkReadyToStart();
        lblStatus.setText(bundle.getString("dashboard.schedule.cancelled"));
    }

    /**
     * ربط مرحلة النشر برسالة الحالة المناسبة في bundle (null يعني عدم تحديث الرسالة)
     */
    static String stageMessageKey(ArticlePublishingService.Stage stage) {
        return switch (stage) {
            case TRANSLATING -> "dashboard.status.translating.title";
            case FETCHING_IMAGE -> "dashboard.status.fetching.image";
            case PUBLISHING -> "dashboard.status.publishing.article";
            case GENERATING_CONTENT -> null;
        };
    }

    @FXML
    public void handleLogout() {
        try {
            log.info("User logging out");

            // Clear the stored credentials
            bloggerService.clearCredentials();

            // Load login screen
            ResourceBundle bundle = localeManager.getResourceBundle();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/hamzatech/publisher/view/login.fxml"),
                    bundle
            );
            Parent root = loader.load();

            Scene scene = AppWindow.createScene(root);
            localeManager.applyOrientation(scene);

            Stage stage = (Stage) lblStatus.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle(bundle.getString("app.title"));

            log.info("User logged out successfully");
        } catch (IOException e) {
            log.error("Failed to logout and return to login screen", e);
            lblStatus.setText(bundle.getString("dashboard.status.logout.failed"));
        }
    }

    @FXML
    public void handleCategories() {
        openScreen(CATEGORIES_FXML, "categories.title", "fth-tag", () -> {
            loadCategoryFilter();
            filterBlogsByCategory();
        }, null);
    }

    @FXML
    public void handleSettings() {
        openScreen(SETTINGS_FXML, "settings.title", "fth-settings", null, null);
    }

    @FXML
    public void handleWordPressSites() {
        openScreen(WORDPRESS_SITES_FXML, "wordpress.sites.title", "fth-globe", this::loadBlogs, null);
    }

    @FXML
    public void handlePublishLogs() {
        openScreen(PUBLISH_LOGS_FXML, "logs.title", "fth-list", null, null);
    }

    /**
     * شاشة الطابور هي الوحيدة التي تحتاج قراءة شيء من متحكمها بعد إغلاقها:
     * الدفعة التي طلب المستخدم استئنافها، إن وُجدت.
     */
    @FXML
    public void handleQueue() {
        this.<PublishQueueController>openScreen(QUEUE_FXML, "queue.title", "fth-clock", this::updateQueueButton,
                controller -> {
                    PublishJob jobToResume = controller.getJobToResume();
                    if (jobToResume != null) {
                        resumeQueuedJob(jobToResume);
                    }
                });
    }

    /**
     * يُظهر زر الطابور بارزًا فقط حين توجد دفعة قابلة للاستئناف — لا حاجة له
     * في الحالة الشائعة (لا شيء تُوقّف).
     */
    private void updateQueueButton() {
        boolean hasResumable = !PublishQueueService.getInstance().getResumableJobs().isEmpty();
        btnQueue.setVisible(hasResumable);
        btnQueue.setManaged(hasResumable);
        overviewController.refresh();
    }

    /**
     * يفتح شاشة تبويبًا في مساحة العمل — انظر {@link WorkspaceTabs}.
     *
     * @param onLeave  تحديث اللوحة عند مغادرة التبويب أو إغلاقه
     * @param onClosed يتلقى متحكم الشاشة عند إغلاقها
     */
    private <C> void openScreen(String fxmlPath, String titleKey, String icon,
                                Runnable onLeave, java.util.function.Consumer<C> onClosed) {
        try {
            workspace.open(fxmlPath, titleKey, icon, () -> {
                overviewController.refresh();
                if (onLeave != null) {
                    onLeave.run();
                }
            }, onClosed);
        } catch (IOException e) {
            handleWindowOpenError(titleKey, e);
        }
    }

    private void handleWindowOpenError(String titleKey, IOException e) {
        lblStatus.setText(e.getMessage());
        log.error("Failed to open window: {}", titleKey, e);
    }
}
