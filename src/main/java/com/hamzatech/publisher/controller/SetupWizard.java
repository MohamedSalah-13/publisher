package com.hamzatech.publisher.controller;

import com.hamzatech.publisher.provider.KeyedProvider;
import com.hamzatech.publisher.provider.ProviderRegistry;
import com.hamzatech.publisher.provider.UnsplashImageProvider;
import com.hamzatech.publisher.service.BloggerService;
import com.hamzatech.publisher.service.ConfigService;
import com.hamzatech.publisher.service.FirstRun;
import com.hamzatech.publisher.service.GoogleCredentialsFile;
import com.hamzatech.publisher.service.KeyCheck;
import com.hamzatech.publisher.service.WordPressService;
import com.hamzatech.publisher.service.WordPressSiteService;
import com.hamzatech.publisher.util.AppPaths;
import com.hamzatech.publisher.util.LocaleManager;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * معالج الإعداد عند أول تشغيل: مفتاح مزود المحتوى، مفتاح الصور، ووجهة النشر.
 * <p>
 * كل مفتاح يُفحص عند المزود قبل حفظه ({@link KeyCheck})، فالخطأ يظهر هنا لا عند أول
 * دفعة نشر. كل خطوة قابلة للتخطّي، ولا شيء هنا لا يمكن فعله لاحقًا من الإعدادات؛
 * المعالج يختصر الطريق ولا يحتكره.
 * <p>
 * مبني بالكود لا بـ FXML كبقية النوافذ المساعدة في هذه الحزمة. قائمة المزودين تأتي
 * من {@link ProviderRegistry}، فمزود جديد يحقق {@link KeyedProvider#apiKeySetting()}
 * يظهر هنا دون تعديل هذا الكلاس.
 */
@Log4j2
final class SetupWizard {

    private enum Tone { INFO, SUCCESS, WARNING, FAILURE }

    private final Stage stage;
    private final ResourceBundle bundle = LocaleManager.getInstance().getResourceBundle();
    private final ConfigService config = ConfigService.getInstance();

    private final List<Step> steps;
    private final TargetStep targetStep;
    private int index;

    private final Label lblProgress = new Label();
    private final Label lblTitle = new Label();
    private final Label lblDescription = new Label();
    private final Hyperlink lnkLanguage = new Hyperlink();
    private final StackPane content = new StackPane();
    private final Label lblFooter = new Label();
    private final Button btnBack = new Button();
    private final Button btnSkip = new Button();
    private final Button btnNext = new Button();
    private final StackPane root = new StackPane();

    SetupWizard(Stage stage) {
        this.stage = stage;
        this.targetStep = new TargetStep();
        this.steps = List.of(
                new WelcomeStep(),
                new KeyStep("setup.ai.title", "setup.ai.description", ConfigService.AI_PROVIDER,
                        ProviderRegistry.contentGenerators().stream()
                                .filter(p -> p.apiKeySetting() != null)
                                .map(p -> (KeyedProvider) p)
                                .toList()),
                new KeyStep("setup.images.title", "setup.images.description", ConfigService.IMAGE_PROVIDER,
                        List.of(ProviderRegistry.imageProvider(UnsplashImageProvider.ID))),
                targetStep);
        build();
        show(0);
    }

    Parent root() {
        return root;
    }

    // ---- الهيكل ----

    private void build() {
        lblProgress.getStyleClass().add("text-muted");
        lblTitle.getStyleClass().add("title-2");
        lblTitle.setWrapText(true);
        lblDescription.getStyleClass().add("text-muted");
        lblDescription.setWrapText(true);

        // اللغة الافتراضية العربية؛ من لا يقرؤها يحتاج مخرجًا من أول شاشة يراها
        boolean arabic = LocaleManager.getInstance().isRTL();
        lnkLanguage.setText(bundle.getString(arabic ? "settings.language.english" : "settings.language.arabic"));
        lnkLanguage.setOnAction(e -> {
            LocaleManager.getInstance().setLocale(arabic ? LocaleManager.ENGLISH : LocaleManager.ARABIC);
            Screens.showSetupWizard(stage);
        });

        Region headerGap = new Region();
        HBox.setHgrow(headerGap, Priority.ALWAYS);
        HBox topRow = new HBox(lblProgress, headerGap, lnkLanguage);
        topRow.setAlignment(Pos.CENTER_LEFT);

        content.setAlignment(Pos.TOP_LEFT);
        content.setMinHeight(300);

        lblFooter.setWrapText(true);
        lblFooter.setManaged(false);
        lblFooter.setVisible(false);

        btnBack.setText(bundle.getString("setup.back"));
        btnBack.setOnAction(e -> show(index - 1));
        btnSkip.setText(bundle.getString("setup.skip"));
        btnSkip.getStyleClass().add("flat");
        btnSkip.setOnAction(e -> advance());
        btnNext.getStyleClass().add("accent");
        btnNext.setDefaultButton(true);
        btnNext.setOnAction(e -> steps.get(index).next(this::advance));

        Region footerGap = new Region();
        HBox.setHgrow(footerGap, Priority.ALWAYS);
        HBox footer = new HBox(10, btnBack, footerGap, btnSkip, btnNext);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14, topRow, lblTitle, lblDescription, content, lblFooter, footer);
        card.getStyleClass().addAll("login-card", "setup-card");
        card.setMaxWidth(640);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        VBox.setVgrow(content, Priority.ALWAYS);

        root.getStyleClass().add("root-pane");
        root.getChildren().add(card);
        StackPane.setMargin(card, new Insets(24));
    }

    private void show(int target) {
        index = target;
        Step step = steps.get(index);
        boolean first = index == 0;
        boolean last = index == steps.size() - 1;

        lblProgress.setText(MessageFormat.format(bundle.getString("setup.step"), index + 1, steps.size()));
        lblTitle.setText(bundle.getString(step.titleKey));
        lblDescription.setText(bundle.getString(step.descriptionKey));
        content.getChildren().setAll(step.node());
        clearFooter();

        lnkLanguage.setVisible(first);
        btnBack.setVisible(!first);
        btnSkip.setVisible(!first);
        btnNext.setText(bundle.getString(first ? "setup.start" : last ? "setup.finish" : "setup.next"));
    }

    /** إلى الخطوة التالية، أو خارج المعالج بعد الأخيرة. */
    private void advance() {
        if (index < steps.size() - 1) {
            show(index + 1);
        } else {
            finish();
        }
    }

    private void finish() {
        try {
            // دخول Google بدأ ثم تُرك في المتصفح لا ينتهي من تلقاء نفسه
            if (!targetStep.googleConnected) {
                targetStep.cancelPendingSignIn();
            }
            FirstRun.markCompleted();
            // بلا وجهة نشر لا شيء يُفعل في اللوحة؛ شاشة الدخول تعرض الخيارين من جديد
            boolean hasTarget = targetStep.googleConnected
                    || !WordPressSiteService.getInstance().getAllSites().isEmpty();
            if (hasTarget) {
                Screens.showDashboard(stage);
            } else {
                Screens.showLogin(stage);
            }
        } catch (IOException | RuntimeException e) {
            log.error("Could not leave the setup wizard", e);
            status(lblFooter, Tone.FAILURE, text("setup.finish.error", reason(e)));
            lblFooter.setManaged(true);
            lblFooter.setVisible(true);
        }
    }

    private void clearFooter() {
        lblFooter.setManaged(false);
        lblFooter.setVisible(false);
    }

    private void setBusy(boolean busy) {
        btnBack.setDisable(busy);
        btnSkip.setDisable(busy);
        btnNext.setDisable(busy);
    }

    // ---- أدوات مشتركة ----

    private String text(String key, Object... args) {
        return MessageFormat.format(bundle.getString(key), args);
    }

    private static String reason(Throwable e) {
        if (e == null) {
            return "";
        }
        return e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static void status(Label label, Tone tone, String message) {
        label.getStyleClass().removeAll("status-success", "status-failure", "status-warning", "text-muted");
        String icon = switch (tone) {
            case SUCCESS -> {
                label.getStyleClass().add("status-success");
                yield "fth-check-circle";
            }
            case FAILURE -> {
                label.getStyleClass().add("status-failure");
                yield "fth-x-circle";
            }
            case WARNING -> {
                label.getStyleClass().add("status-warning");
                yield "fth-alert-triangle";
            }
            case INFO -> {
                label.getStyleClass().add("text-muted");
                yield "fth-info";
            }
        };
        FontIcon graphic = new FontIcon(icon);
        graphic.setIconSize(16);
        label.setGraphic(graphic);
        label.setGraphicTextGap(8);
        label.setText(message);
    }

    private static Label statusLabel() {
        Label label = new Label();
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-bold");
        return label;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-muted");
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static Button iconButton(String text, String icon) {
        Button button = new Button(text);
        FontIcon graphic = new FontIcon(icon);
        graphic.setIconSize(16);
        button.setGraphic(graphic);
        return button;
    }

    /**
     * نداء خارج خيط الواجهة.
     *
     * @param blocking يعطّل أزرار التنقل حتى ينتهي النداء. لا يصلح لما قد لا ينتهي
     *                 أبدًا (تسجيل دخول Google ينتظر المتصفح بلا مهلة)
     */
    private <T> void runInBackground(String threadName, boolean blocking, Callable<T> work,
                                     Consumer<T> onDone, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> {
            if (blocking) {
                setBusy(false);
            }
            onDone.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            if (blocking) {
                setBusy(false);
            }
            onError.accept(task.getException());
        });
        if (blocking) {
            setBusy(true);
        }
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            log.warn("Could not open {} in the browser: {}", url, e.getMessage());
        }
    }

    // ---- الخطوات ----

    private abstract static class Step {
        final String titleKey;
        final String descriptionKey;
        private Node node;

        Step(String titleKey, String descriptionKey) {
            this.titleKey = titleKey;
            this.descriptionKey = descriptionKey;
        }

        /** يُبنى مرة واحدة، فما كتبه المستخدم يبقى عند الرجوع إلى الخطوة. */
        final Node node() {
            if (node == null) {
                node = build();
            }
            return node;
        }

        abstract Node build();

        /**
         * ضُغط "التالي". الخطوة تستدعي {@code proceed} حين تنتهي — فورًا أو بعد نداء
         * شبكة — ولا تستدعيه إن أرادت إبقاء المستخدم فيها.
         */
        void next(Runnable proceed) {
            proceed.run();
        }
    }

    private final class WelcomeStep extends Step {

        WelcomeStep() {
            super("setup.welcome.title", "setup.welcome.description");
        }

        @Override
        Node build() {
            VBox box = new VBox(14,
                    row("fth-cpu", "setup.welcome.ai"),
                    row("fth-image", "setup.welcome.images"),
                    row("fth-send", "setup.welcome.target"),
                    hint(bundle.getString("setup.welcome.note")));
            box.setPadding(new Insets(8, 0, 0, 0));
            return box;
        }

        private Node row(String icon, String key) {
            FontIcon graphic = new FontIcon(icon);
            graphic.setIconSize(18);
            graphic.getStyleClass().add("accent");
            Label label = new Label(bundle.getString(key), graphic);
            label.setGraphicTextGap(12);
            label.setWrapText(true);
            label.setMinHeight(Region.USE_PREF_SIZE);
            return label;
        }
    }

    /**
     * خطوة مفتاح API: اختيار المزود (إن وُجد أكثر من واحد)، لصق المفتاح، فحصه.
     */
    private final class KeyStep extends Step {

        private final String selectionSetting;
        private final List<KeyedProvider> providers;

        private final ComboBox<KeyedProvider> cmbProvider = new ComboBox<>();
        private final PasswordField txtKey = new PasswordField();
        private final Hyperlink lnkGetKey = new Hyperlink();
        private final Label lblStatus = statusLabel();

        /** آخر ما فُحص: المزود والمفتاح معًا، فتغيير أيٍّ منهما يُبطل النتيجة. */
        private String checkedFor;
        private KeyCheck.Result checkedResult;

        /**
         * @param selectionSetting الإعداد الذي يُحفظ فيه معرّف المزود المختار
         */
        KeyStep(String titleKey, String descriptionKey, String selectionSetting, List<KeyedProvider> providers) {
            super(titleKey, descriptionKey);
            this.selectionSetting = selectionSetting;
            this.providers = providers;
        }

        @Override
        Node build() {
            cmbProvider.getItems().setAll(providers);
            cmbProvider.setMaxWidth(Double.MAX_VALUE);
            cmbProvider.setConverter(new StringConverter<>() {
                @Override
                public String toString(KeyedProvider provider) {
                    return provider == null ? "" : provider.displayName();
                }

                @Override
                public KeyedProvider fromString(String name) {
                    return null;
                }
            });
            String current = config.get(selectionSetting);
            cmbProvider.getSelectionModel().select(providers.stream()
                    .filter(p -> p.id().equals(current))
                    .findFirst().orElse(providers.get(0)));
            cmbProvider.valueProperty().addListener((obs, was, now) -> providerChanged());

            Label lblProvider = fieldLabel(bundle.getString("setup.key.provider"));
            boolean choice = providers.size() > 1;
            lblProvider.setVisible(choice);
            lblProvider.setManaged(choice);
            cmbProvider.setVisible(choice);
            cmbProvider.setManaged(choice);

            lnkGetKey.setGraphic(smallIcon("fth-external-link"));
            lnkGetKey.setOnAction(e -> openInBrowser(provider().apiKeyPage()));

            Button btnTest = iconButton(bundle.getString("setup.key.test"), "fth-zap");
            btnTest.setOnAction(e -> test());
            HBox keyRow = new HBox(8, txtKey, btnTest);
            HBox.setHgrow(txtKey, Priority.ALWAYS);

            providerChanged();
            return new VBox(8, lblProvider, cmbProvider,
                    fieldLabel(bundle.getString("setup.key.label")), keyRow, lnkGetKey, lblStatus);
        }

        private Node smallIcon(String literal) {
            FontIcon icon = new FontIcon(literal);
            icon.setIconSize(14);
            return icon;
        }

        private KeyedProvider provider() {
            return cmbProvider.getValue();
        }

        private void providerChanged() {
            KeyedProvider provider = provider();
            lnkGetKey.setText(text("setup.key.get", provider.displayName()));
            boolean hasPage = provider.apiKeyPage() != null;
            lnkGetKey.setVisible(hasPage);
            lnkGetKey.setManaged(hasPage);
            txtKey.setPromptText(bundle.getString(
                    config.hasKey(provider.apiKeySetting()) ? "setup.key.prompt.saved" : "setup.key.prompt"));
            lblStatus.setGraphic(null);
            lblStatus.setText("");
        }

        private String key() {
            return txtKey.getText() == null ? "" : txtKey.getText().trim();
        }

        private static String fingerprint(KeyedProvider provider, String key) {
            return provider.id() + '\n' + key;
        }

        private void test() {
            if (key().isEmpty()) {
                status(lblStatus, Tone.WARNING, bundle.getString("setup.key.empty"));
                return;
            }
            check(result -> { });
        }

        private void check(Consumer<KeyCheck.Result> then) {
            KeyedProvider provider = provider();
            String key = key();
            status(lblStatus, Tone.INFO, bundle.getString("setup.key.checking"));
            runInBackground("setup-key-check", true, () -> provider.checkKey(key), result -> {
                checkedFor = fingerprint(provider, key);
                checkedResult = result;
                showResult(result);
                then.accept(result);
            }, error -> {
                // checkKey لا يرمي بحكم عقده؛ مزود يخالف ذلك لا يُعامل كمفتاح مرفوض
                log.warn("Key check for {} threw", provider.id(), error);
                checkedFor = fingerprint(provider, key);
                checkedResult = new KeyCheck.Result(KeyCheck.Outcome.FAILED, 0);
                showResult(checkedResult);
                then.accept(checkedResult);
            });
        }

        private void showResult(KeyCheck.Result result) {
            switch (result.outcome()) {
                case VALID -> status(lblStatus, Tone.SUCCESS, bundle.getString("setup.key.valid"));
                case REJECTED -> status(lblStatus, Tone.FAILURE, bundle.getString("setup.key.rejected"));
                case UNREACHABLE -> status(lblStatus, Tone.WARNING,
                        bundle.getString("setup.key.unreachable") + " " + bundle.getString("setup.key.save.anyway"));
                case FAILED -> status(lblStatus, Tone.WARNING,
                        text("setup.key.failed", String.valueOf(result.httpStatus()))
                                + " " + bundle.getString("setup.key.save.anyway"));
                case UNSUPPORTED -> {
                    lblStatus.setGraphic(null);
                    lblStatus.setText("");
                }
            }
        }

        @Override
        void next(Runnable proceed) {
            KeyedProvider provider = provider();
            String key = key();

            if (key.isEmpty()) {
                // حقل فارغ = لا تغيير في المفتاح؛ لكن اختيار مزود له مفتاح محفوظ يُحترم
                if (config.hasKey(provider.apiKeySetting())) {
                    save(provider, null, proceed);
                } else {
                    proceed.run();
                }
                return;
            }

            boolean alreadyChecked = fingerprint(provider, key).equals(checkedFor) && checkedResult != null;
            if (alreadyChecked) {
                // ضغطة ثانية على مفتاح لم يتأكد فحصه = "احفظه على مسؤوليتي"؛ المرفوض لا يُحفظ
                if (checkedResult.outcome() == KeyCheck.Outcome.REJECTED) {
                    showResult(checkedResult);
                } else {
                    save(provider, key, proceed);
                }
                return;
            }

            check(result -> {
                if (result.valid() || result.outcome() == KeyCheck.Outcome.UNSUPPORTED) {
                    save(provider, key, proceed);
                }
            });
        }

        /**
         * @param key {@code null} يُبقي المفتاح المحفوظ ويحدّث المزود المختار فقط
         */
        private void save(KeyedProvider provider, String key, Runnable proceed) {
            try {
                if (key != null) {
                    config.set(provider.apiKeySetting(), key);
                }
                config.set(selectionSetting, provider.id());
                config.saveConfig();
                proceed.run();
            } catch (IOException e) {
                log.error("Could not save the key for {}", provider.id(), e);
                status(lblStatus, Tone.FAILURE, text("setup.key.save.error", reason(e)));
            }
        }
    }

    /**
     * خطوة وجهة النشر: موقع ووردبريس (بلا أي حساب Google) أو مدونة Blogger.
     */
    private final class TargetStep extends Step {

        private final WordPressService wordPressService = new WordPressService();
        private final WordPressSiteService siteService = WordPressSiteService.getInstance();

        private final ToggleButton tglWordPress = new ToggleButton();
        private final TextField txtUrl = new TextField();
        private final TextField txtUsername = new TextField();
        private final PasswordField txtPassword = new PasswordField();
        private final Button btnSaveSite = iconButton("", "fth-check");
        private final Label lblSite = statusLabel();
        private boolean siteSaved;

        private final Label lblCredentials = statusLabel();
        private final Button btnSignIn = iconButton("", "fth-log-in");
        private final Label lblGoogle = statusLabel();
        boolean googleConnected;
        /** لا تُلمس خدمة Blogger عند الخروج إن لم يُضغط زر الدخول أصلًا. */
        private boolean signInStarted;

        TargetStep() {
            super("setup.target.title", "setup.target.description");
        }

        @Override
        Node build() {
            ToggleButton tglBlogger = new ToggleButton(bundle.getString("setup.target.blogger"));
            tglWordPress.setText(bundle.getString("setup.target.wordpress"));
            ToggleGroup group = new ToggleGroup();
            tglWordPress.setToggleGroup(group);
            tglBlogger.setToggleGroup(group);
            // ووردبريس أولًا: لا يحتاج ملف OAuth ولا مشروعًا في Google Cloud
            tglWordPress.setSelected(true);
            // الضغط على الزر المختار يلغي اختياره في ToggleGroup؛ هنا لا معنى لـ"لا شيء"
            group.selectedToggleProperty().addListener((obs, was, now) -> {
                if (now == null) {
                    was.setSelected(true);
                }
            });

            Node wordPress = buildWordPress();
            Node blogger = buildBlogger();
            wordPress.visibleProperty().bind(tglWordPress.selectedProperty());
            wordPress.managedProperty().bind(tglWordPress.selectedProperty());
            blogger.visibleProperty().bind(tglBlogger.selectedProperty());
            blogger.managedProperty().bind(tglBlogger.selectedProperty());

            return new VBox(14, new HBox(8, tglWordPress, tglBlogger), wordPress, blogger);
        }

        // -- ووردبريس --

        private Node buildWordPress() {
            txtUrl.setPromptText(bundle.getString("setup.wp.url.prompt"));
            btnSaveSite.setText(bundle.getString("setup.wp.save"));
            btnSaveSite.setOnAction(e -> saveSite(() -> { }));
            return new VBox(8,
                    fieldLabel(bundle.getString("setup.wp.url")), txtUrl,
                    fieldLabel(bundle.getString("setup.wp.username")), txtUsername,
                    fieldLabel(bundle.getString("setup.wp.password")), txtPassword,
                    hint(bundle.getString("setup.wp.hint")),
                    btnSaveSite, lblSite);
        }

        private boolean siteFieldsFilled() {
            return !txtUrl.getText().isBlank() && !txtUsername.getText().isBlank() && !txtPassword.getText().isBlank();
        }

        private void saveSite(Runnable then) {
            if (!siteFieldsFilled()) {
                status(lblSite, Tone.WARNING, bundle.getString("setup.wp.fill"));
                return;
            }
            // التطبيع مرة واحدة حتى يذهب العنوان نفسه إلى الفحص والحفظ
            String url = WordPressService.normalizeUrl(txtUrl.getText());
            String username = txtUsername.getText().trim();
            String password = txtPassword.getText().trim();
            if (!WordPressService.isSecureUrl(url)) {
                status(lblSite, Tone.FAILURE, bundle.getString("setup.wp.insecure"));
                return;
            }
            txtUrl.setText(url);

            status(lblSite, Tone.INFO, bundle.getString("setup.wp.testing"));
            btnSaveSite.setDisable(true);
            runInBackground("setup-wp-test", true, () -> {
                wordPressService.testConnection(url, username, password);
                return siteService.addSite(siteName(url), url, username, password);
            }, site -> {
                siteSaved = true;
                txtUrl.setDisable(true);
                txtUsername.setDisable(true);
                txtPassword.setDisable(true);
                status(lblSite, Tone.SUCCESS, bundle.getString("setup.wp.saved"));
                then.run();
            }, error -> {
                log.warn("WordPress site check failed in setup", error);
                btnSaveSite.setDisable(false);
                status(lblSite, Tone.FAILURE, text("setup.wp.failed", reason(error)));
            });
        }

        private String siteName(String url) {
            HttpUrl parsed = HttpUrl.parse(url);
            return parsed != null ? parsed.host() : url;
        }

        // -- Blogger --

        private Node buildBlogger() {
            Hyperlink lnkConsole = new Hyperlink(bundle.getString("setup.blogger.console"));
            lnkConsole.setOnAction(e -> openInBrowser("https://console.cloud.google.com/apis/credentials"));

            Button btnChoose = iconButton(bundle.getString("setup.blogger.choose"), "fth-upload");
            btnChoose.setOnAction(e -> chooseCredentials());

            btnSignIn.setText(bundle.getString("setup.blogger.signin"));
            btnSignIn.setOnAction(e -> signIn());

            refreshCredentials(null);
            return new VBox(10,
                    hint(bundle.getString("setup.blogger.hint")), lnkConsole,
                    lblCredentials, btnChoose,
                    btnSignIn, lblGoogle);
        }

        /**
         * @param justInstalled رسالة تُعرض بدل "الملف موجود" بعد اختيار ملف للتو
         */
        private void refreshCredentials(String justInstalled) {
            boolean available = BloggerService.getInstance().hasCredentialsFile();
            if (available) {
                status(lblCredentials, Tone.SUCCESS,
                        justInstalled != null ? justInstalled : bundle.getString("setup.blogger.file.found"));
            } else {
                status(lblCredentials, Tone.WARNING, bundle.getString("setup.blogger.file.missing"));
            }
            btnSignIn.setDisable(!available || googleConnected);
        }

        private void chooseCredentials() {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(bundle.getString("setup.blogger.choose"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
            File chosen = chooser.showOpenDialog(stage);
            if (chosen == null) {
                return;
            }
            switch (GoogleCredentialsFile.check(chosen.toPath())) {
                case WEB_CLIENT -> status(lblCredentials, Tone.FAILURE, bundle.getString("setup.blogger.file.web"));
                case NOT_CREDENTIALS -> status(lblCredentials, Tone.FAILURE, bundle.getString("setup.blogger.file.invalid"));
                case UNREADABLE -> status(lblCredentials, Tone.FAILURE, bundle.getString("setup.blogger.file.unreadable"));
                case OK -> {
                    try {
                        installCredentials(chosen.toPath());
                        refreshCredentials(bundle.getString("setup.blogger.file.installed"));
                    } catch (IOException | RuntimeException e) {
                        log.error("Could not copy the chosen credentials file", e);
                        status(lblCredentials, Tone.FAILURE, text("setup.blogger.file.error", reason(e)));
                    }
                }
            }
        }

        /**
         * الملف المختار يصير هو المستخدَم ({@link GoogleCredentialsFile#locate}). إن
         * اختلف عن المحفوظ فالجلسة السابقة صدرت لعميل OAuth آخر ولا تصلح مع هذا،
         * فتُمسح ليبدأ دخول جديد بدل فشل غامض عند أول نداء.
         */
        private void installCredentials(Path chosen) throws IOException {
            Path current = AppPaths.dataDir().resolve(GoogleCredentialsFile.FILE_NAME);
            boolean unchanged = Files.isRegularFile(current) && Files.mismatch(chosen, current) == -1;
            GoogleCredentialsFile.install(chosen);
            if (!unchanged) {
                BloggerService blogger = BloggerService.getInstance();
                blogger.cancelLogin();
                blogger.clearCredentials();
                googleConnected = false;
                signInStarted = false;
                lblGoogle.setGraphic(null);
                lblGoogle.setText("");
            }
        }

        void cancelPendingSignIn() {
            if (signInStarted) {
                BloggerService.getInstance().cancelLogin();
            }
        }

        private void signIn() {
            signInStarted = true;
            status(lblGoogle, Tone.INFO, bundle.getString("setup.blogger.signing"));
            btnSignIn.setDisable(true);
            runInBackground("setup-google-login", false, () -> {
                BloggerService.getInstance().login();
                return null;
            }, done -> {
                googleConnected = true;
                status(lblGoogle, Tone.SUCCESS, bundle.getString("setup.blogger.signed"));
            }, error -> {
                log.error("Google sign-in failed in setup", error);
                btnSignIn.setDisable(false);
                status(lblGoogle, Tone.FAILURE, text("setup.blogger.failed", reason(error)));
            });
        }

        @Override
        void next(Runnable proceed) {
            // من ملأ بيانات الموقع وضغط "إنهاء" مباشرة قصد حفظها، لا رميها
            if (tglWordPress.isSelected() && siteFieldsFilled() && !siteSaved) {
                saveSite(proceed);
            } else {
                proceed.run();
            }
        }
    }
}
