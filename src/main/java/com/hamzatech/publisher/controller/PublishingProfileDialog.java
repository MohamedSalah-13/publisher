package com.hamzatech.publisher.controller;

import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.model.PublishingProfile;
import com.hamzatech.publisher.provider.FacebookPageSharer;
import com.hamzatech.publisher.provider.ProviderRegistry;
import com.hamzatech.publisher.provider.SocialSharer;
import com.hamzatech.publisher.provider.TelegramSharer;
import com.hamzatech.publisher.service.ConfigService;
import com.hamzatech.publisher.service.PublishingProfileService;
import com.hamzatech.publisher.util.AppIcons;
import com.hamzatech.publisher.util.LocaleManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * نافذة إعدادات نشر خاصة بوجهة واحدة، فوق الإعدادات العامة.
 * <p>
 * كل قائمة هنا أول عنصر فيها "استخدام الافتراضي" ويحمل قيمته الحالية بين
 * قوسين، حتى يرى المستخدم ما سيُطبَّق فعليًا دون فتح شاشة الإعدادات العامة.
 * <p>
 * نُقلت من {@link DashboardController} كما هي.
 */
@Log4j2
final class PublishingProfileDialog {

    /**
     * محدَّد وحده: (تسمية معروضة، القيمة المخزَّنة). القيمة {@code null} تعني
     * "استخدم الإعداد العام" — نفس معنى الحقل الفارغ في {@link PublishingProfile}.
     */
    private record Choice(String label, String value) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final ResourceBundle bundle = LocaleManager.getInstance().getResourceBundle();
    private final ConfigService configService = ConfigService.getInstance();
    private final PublishingProfileService profileService = PublishingProfileService.getInstance();
    private final PublishTarget target;
    private final Consumer<String> onChanged;
    private final Function<Throwable, String> errorText;
    private final Stage dialog = new Stage();

    /**
     * @param categories التصنيفات المعروضة حاليًا للوجهة؛ قد تكون فارغة إن لم
     *                   تُحمَّل بعد، وحقل التصنيف قابل للكتابة فلا يمنع ذلك اختيار اسم يدويًا
     * @param onChanged  يُستدعى برسالة الحالة بعد الحفظ أو الإعادة، لتحدّث اللوحة
     *                   حالتها وتعيد تحميل التصنيفات (قد يتغير التصنيف الافتراضي)
     * @param errorText  تحويل الاستثناء إلى رسالة مفهومة، كما في بقية اللوحة
     */
    PublishingProfileDialog(PublishTarget target, List<String> categories, Consumer<String> onChanged,
                            Function<Throwable, String> errorText) {
        this.target = target;
        this.onChanged = onChanged;
        this.errorText = errorText;
        build(categories);
    }

    void show() {
        dialog.show();
    }

    private void build(List<String> categories) {
        PublishingProfile existing = profileService.getProfile(target.getId());

        ComboBox<Choice> cmbAi = overrideCombo(
                useDefault(ProviderRegistry.contentGenerator(configService.getAIProvider()).displayName()),
                existing == null ? null : existing.getAiProvider(),
                ProviderRegistry.contentGenerators().stream()
                        .map(g -> new Choice(g.displayName(), g.id()))
                        .toList());

        ComboBox<Choice> cmbImage = overrideCombo(
                useDefault(ProviderRegistry.imageProvider(configService.getImageProvider()).displayName()),
                existing == null ? null : existing.getImageProvider(),
                ProviderRegistry.imageProviders().stream()
                        .map(p -> new Choice(p.displayName(), p.id()))
                        .toList());

        List<Choice> languageChoices = List.of(
                new Choice(bundle.getString("settings.language.arabic"), "arabic"),
                new Choice(bundle.getString("settings.language.english"), "english"));
        ComboBox<Choice> cmbContentLang = overrideCombo(
                useDefault(languageLabel(configService.getContentLanguage())),
                existing == null ? null : existing.getContentLanguage(), languageChoices);
        ComboBox<Choice> cmbTitleLang = overrideCombo(
                useDefault(languageLabel(configService.getTitleLanguage())),
                existing == null ? null : existing.getTitleLanguage(), languageChoices);

        List<Choice> slugChoices = List.of(
                new Choice(bundle.getString("settings.slug.auto"), "auto"),
                new Choice(bundle.getString("settings.slug.custom"), "custom"),
                new Choice(bundle.getString("settings.slug.english"), "english"));
        ComboBox<Choice> cmbSlug = overrideCombo(
                useDefault(slugLabel(configService.getSlugType())),
                existing == null ? null : existing.getSlugType(), slugChoices);

        List<Choice> statusChoices = List.of(
                new Choice(bundle.getString("dashboard.profile.post.status.publish"), ConfigService.POST_STATUS_PUBLISH),
                new Choice(bundle.getString("dashboard.profile.post.status.draft"), ConfigService.POST_STATUS_DRAFT));
        ComboBox<Choice> cmbPostStatus = overrideCombo(
                useDefault(postStatusLabel(configService.getPostStatus())),
                existing == null ? null : existing.getPostStatus(), statusChoices);

        CheckBox chkOverrideDelay = new CheckBox(MessageFormat.format(
                bundle.getString("dashboard.profile.override.delay"), configService.getPublishDelay()));
        Spinner<Integer> spnDelay = new Spinner<>(0, 3600,
                existing != null && existing.getPublishDelaySeconds() != null
                        ? existing.getPublishDelaySeconds() : configService.getPublishDelay());
        spnDelay.setEditable(true);
        spnDelay.setMaxWidth(120);
        spnDelay.disableProperty().bind(chkOverrideDelay.selectedProperty().not());
        chkOverrideDelay.setSelected(existing != null && existing.getPublishDelaySeconds() != null);

        ComboBox<String> cmbCategory = new ComboBox<>();
        cmbCategory.setEditable(true);
        cmbCategory.setMaxWidth(Double.MAX_VALUE);
        cmbCategory.setPromptText(bundle.getString("dashboard.profile.default.category.none"));
        cmbCategory.getItems().addAll(categories);
        if (existing != null && existing.getDefaultCategory() != null) {
            cmbCategory.setValue(existing.getDefaultCategory());
        }

        TextField txtTelegram = new TextField(existing == null ? null : existing.getTelegramChat());
        txtTelegram.setPromptText(bundle.getString("dashboard.profile.telegram.chat.prompt"));
        TextField txtFacebook = new TextField(existing == null ? null : existing.getFacebookPageId());
        txtFacebook.setPromptText(bundle.getString("dashboard.profile.facebook.page.prompt"));
        Label sharingHint = hint(bundle.getString("dashboard.profile.sharing.hint"));

        // missingToken يعيد null حين يكون التوكن مضبوطًا، وVBox يرفض عقدة null في
        // مُنشئه نفسه — فتُصفّى القائمة قبل بنائه لا بعده
        VBox form = new VBox(12, Stream.of(
                sectionLabel("dashboard.profile.section.content"),
                labeled("dashboard.profile.ai.provider", cmbAi),
                labeled("dashboard.profile.image.provider", cmbImage),
                labeled("dashboard.profile.content.language", cmbContentLang),
                labeled("dashboard.profile.title.language", cmbTitleLang),
                labeled("dashboard.profile.slug.type", cmbSlug),
                new Separator(),
                sectionLabel("dashboard.profile.section.publishing"),
                labeled("dashboard.profile.post.status", cmbPostStatus),
                chkOverrideDelay, spnDelay,
                labeled("dashboard.profile.default.category", cmbCategory),
                new Separator(),
                sectionLabel("dashboard.profile.section.sharing"),
                labeled("dashboard.profile.telegram.chat", txtTelegram),
                missingToken(TelegramSharer.ID),
                labeled("dashboard.profile.facebook.page", txtFacebook),
                missingToken(FacebookPageSharer.ID),
                sharingHint).filter(Objects::nonNull).toArray(Node[]::new));
        form.setPadding(new Insets(20));

        Button btnSave = new Button(bundle.getString("dashboard.profile.save.button"));
        btnSave.getStyleClass().add("accent");
        Button btnReset = new Button(bundle.getString("dashboard.profile.reset.button"));
        btnReset.getStyleClass().add("danger");
        Button btnCancel = new Button(bundle.getString("categories.cancel.button"));

        HBox actions = new HBox(10, btnReset, new HBox(), btnCancel, btnSave);
        HBox.setHgrow(actions.getChildren().get(1), Priority.ALWAYS);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(12, 20, 16, 20));

        // النموذج قد يتجاوز ارتفاع النافذة (سبعة حقول)، فيُغلَّف بـScrollPane حتى
        // لا تُقصّ أزرار الحفظ/الإعادة/الإلغاء خارج الرؤية كما حدث في أول تجربة
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);

        BorderPane root = new BorderPane();
        root.setCenter(scroll);
        root.setBottom(actions);

        dialog.setTitle(MessageFormat.format(bundle.getString("dashboard.profile.title"), target.getName()));
        dialog.initModality(Modality.APPLICATION_MODAL);
        Scene scene = new Scene(root, 440, 680);
        LocaleManager.getInstance().applyOrientation(scene);
        dialog.setScene(scene);
        AppIcons.applyTo(dialog);

        btnCancel.setOnAction(e -> dialog.close());

        btnReset.setOnAction(e -> {
            try {
                profileService.deleteProfile(target.getId());
                dialog.close();
                onChanged.accept(bundle.getString("dashboard.profile.reset.done"));
            } catch (IOException ex) {
                showError(ex);
            }
        });

        btnSave.setOnAction(e -> {
            PublishingProfile toSave = new PublishingProfile(target.getId());
            toSave.setAiProvider(cmbAi.getValue().value());
            toSave.setImageProvider(cmbImage.getValue().value());
            toSave.setContentLanguage(cmbContentLang.getValue().value());
            toSave.setTitleLanguage(cmbTitleLang.getValue().value());
            toSave.setSlugType(cmbSlug.getValue().value());
            toSave.setPostStatus(cmbPostStatus.getValue().value());
            toSave.setPublishDelaySeconds(chkOverrideDelay.isSelected() ? spnDelay.getValue() : null);
            String category = cmbCategory.getValue();
            toSave.setDefaultCategory(category == null || category.isBlank() ? null : category.trim());
            toSave.setTelegramChat(blankToNull(txtTelegram.getText()));
            toSave.setFacebookPageId(blankToNull(txtFacebook.getText()));

            try {
                profileService.saveProfile(toSave);
                dialog.close();
                onChanged.accept(bundle.getString("dashboard.profile.saved"));
            } catch (IOException ex) {
                showError(ex);
            }
        });
    }

    private void showError(IOException ex) {
        log.error("Failed to save publishing profile", ex);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(bundle.getString("dashboard.profile.error.title"));
        alert.setHeaderText(null);
        alert.setContentText(MessageFormat.format(
                bundle.getString("dashboard.profile.error.save"), errorText.apply(ex)));
        alert.showAndWait();
    }

    private String useDefault(String currentDefault) {
        return MessageFormat.format(bundle.getString("dashboard.profile.use.default"), currentDefault);
    }

    private ComboBox<Choice> overrideCombo(String defaultLabel, String currentOverride, List<Choice> concreteChoices) {
        ComboBox<Choice> combo = new ComboBox<>();
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.getItems().add(new Choice(defaultLabel, null));
        combo.getItems().addAll(concreteChoices);
        combo.setValue(combo.getItems().stream()
                .filter(c -> Objects.equals(c.value(), currentOverride))
                .findFirst()
                .orElse(combo.getItems().get(0)));
        return combo;
    }

    private String languageLabel(String value) {
        return "english".equals(value) ? bundle.getString("settings.language.english")
                : bundle.getString("settings.language.arabic");
    }

    private String postStatusLabel(String value) {
        return ConfigService.POST_STATUS_DRAFT.equals(value)
                ? bundle.getString("dashboard.profile.post.status.draft")
                : bundle.getString("dashboard.profile.post.status.publish");
    }

    private String slugLabel(String value) {
        return switch (value) {
            case "custom" -> bundle.getString("settings.slug.custom");
            case "english" -> bundle.getString("settings.slug.english");
            default -> bundle.getString("settings.slug.auto");
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** تنبيه تحت حقل الشبكة إن لم يُضبط توكنها في الإعدادات، وإلا {@code null}. */
    private Label missingToken(String sharerId) {
        return ProviderRegistry.socialSharers().stream()
                .filter(s -> s.id().equals(sharerId) && !s.isConfigured())
                .map(SocialSharer::displayName)
                .findFirst()
                .map(name -> {
                    Label label = hint(MessageFormat.format(
                            bundle.getString("dashboard.profile.sharing.token.missing"), name));
                    label.getStyleClass().add("warning");
                    return label;
                })
                .orElse(null);
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().addAll("text-muted", "text-small");
        return label;
    }

    private Label sectionLabel(String key) {
        Label label = new Label(bundle.getString(key));
        label.getStyleClass().add("title-4");
        return label;
    }

    private VBox labeled(String key, Node control) {
        return new VBox(4, new Label(bundle.getString(key)), control);
    }
}
