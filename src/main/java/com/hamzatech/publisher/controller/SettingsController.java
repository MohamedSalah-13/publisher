package com.hamzatech.publisher.controller;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.controls.ToggleSwitch;
import com.hamzatech.publisher.service.ConfigService;
import com.hamzatech.publisher.util.LocaleManager;
import com.hamzatech.publisher.util.ThemeManager;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import lombok.extern.log4j.Log4j2;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.ResourceBundle;

@Log4j2
public class SettingsController {

    private final ConfigService configService = ConfigService.getInstance();
    private final LocaleManager localeManager = LocaleManager.getInstance();
    @FXML
    private RadioButton rbOpenAI;
    @FXML
    private RadioButton rbGemini;
    @FXML
    private RadioButton rbClaude;
    @FXML
    private RadioButton rbUnsplash;
    @FXML
    private RadioButton rbDallE;
    @FXML
    private PasswordTextField txtOpenAIKey;
    @FXML
    private PasswordTextField txtGeminiKey;
    @FXML
    private PasswordTextField txtUnsplashKey;
    @FXML
    private PasswordTextField txtClaudeKey;
    @FXML
    private PasswordTextField txtTelegramToken;
    @FXML
    private PasswordTextField txtFacebookToken;
    @FXML
    private TextField txtOpenAIModel;
    @FXML
    private TextField txtGeminiModel;
    @FXML
    private TextField txtClaudeModel;
    @FXML
    private TextField txtImageModel;
    @FXML
    private ComboBox<Choice> cmbImageQuality;
    @FXML
    private ComboBox<Choice> cmbArticleLength;
    @FXML
    private CheckBox chkArticleFaq;
    @FXML
    private TextArea txtArticleInstructions;
    @FXML
    private CheckBox chkPublishAsDraft;
    @FXML
    private CheckBox chkSeoAi;
    @FXML
    private CheckBox chkQualityCheck;
    @FXML
    private CheckBox chkInternalLinks;
    @FXML
    private Slider sliderDelay;
    @FXML
    private Label lblDelayValue;
    @FXML
    private RadioButton rbSlugAuto;
    @FXML
    private RadioButton rbSlugCustom;
    @FXML
    private RadioButton rbSlugEnglish;
    @FXML
    private Label lblSlugNote;
    @FXML
    private RadioButton rbTitleArabic;
    @FXML
    private RadioButton rbTitleEnglish;
    @FXML
    private RadioButton rbContentArabic;
    @FXML
    private RadioButton rbContentEnglish;
    @FXML
    private Button btnSave;
    @FXML
    private Button btnCancel;
    @FXML
    private CheckBox chkSkipDuplicates;
    @FXML
    private ToggleSwitch toggleDarkMode;
    private ResourceBundle bundle;

    /** خيار في قائمة منسدلة (طول المقال، جودة الصورة): القيمة المخزَّنة وتسميتها المعروضة. */
    private record Choice(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    @FXML
    public void initialize() {
        log.info("Initializing SettingsController");
        bundle = localeManager.getResourceBundle();

        // ربط Slider بـ Label
        setupDelaySlider();

        // زر إظهار/إخفاء بجانب كل مفتاح
        setupKeyField(txtOpenAIKey);
        setupKeyField(txtGeminiKey);
        setupKeyField(txtUnsplashKey);
        setupKeyField(txtClaudeKey);
        setupKeyField(txtTelegramToken);
        setupKeyField(txtFacebookToken);

        cmbArticleLength.getItems().setAll(
                new Choice("short", bundle.getString("settings.article.length.short")),
                new Choice("medium", bundle.getString("settings.article.length.medium")),
                new Choice("long", bundle.getString("settings.article.length.long")));

        cmbImageQuality.getItems().setAll(
                new Choice("low", bundle.getString("settings.image.quality.low")),
                new Choice("medium", bundle.getString("settings.image.quality.medium")),
                new Choice("high", bundle.getString("settings.image.quality.high")));

        loadCurrentSettings();

        toggleDarkMode.setSelected(ThemeManager.getInstance().isDarkMode());
        toggleDarkMode.selectedProperty().addListener((obs, oldVal, newVal) -> ThemeManager.getInstance().setDarkMode(newVal));
    }

    /**
     * إعداد Slider للتأخير مع ربطه بـ Label
     */
    private void setupDelaySlider() {
        sliderDelay.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                int delayValue = newValue.intValue();
                updateDelayLabel(delayValue);
            }
        });

        // تحديث Label عند التهيئة
        updateDelayLabel((int) sliderDelay.getValue());

        // إعداد خيارات Slug
        setupSlugOptions();
    }

    /**
     * تركيب أيقونة عين قابلة للنقر داخل حقل مفتاح API للتبديل بين الإظهار والإخفاء.
     * الحقل مخفي افتراضيًا حتى لا يظهر المفتاح لمن ينظر إلى الشاشة.
     */
    private void setupKeyField(PasswordTextField field) {
        FontIcon icon = new FontIcon("fth-eye-off");
        icon.setCursor(Cursor.HAND);
        icon.getStyleClass().add("text-muted");

        Tooltip tooltip = new Tooltip(bundle.getString("settings.key.reveal"));
        Tooltip.install(icon, tooltip);

        icon.setOnMouseClicked(e -> {
            boolean reveal = !field.getRevealPassword();
            field.setRevealPassword(reveal);
            icon.setIconLiteral(reveal ? "fth-eye" : "fth-eye-off");
            tooltip.setText(bundle.getString(reveal ? "settings.key.hide" : "settings.key.reveal"));
        });

        field.setRight(icon);
    }

    /**
     * إعداد خيارات Slug
     */
    private void setupSlugOptions() {
        // عرض ملاحظة عند اختيار الروابط الإنجليزية
        rbSlugEnglish.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                lblSlugNote.setVisible(true);
            } else {
                lblSlugNote.setVisible(false);
            }
        });
    }

    /**
     * تحديث نص Label لعرض القيمة الحالية
     */
    private void updateDelayLabel(int seconds) {
        String unit = bundle.getString("settings.publish.delay.unit");
        lblDelayValue.setText(seconds + " " + unit);
    }

    /**
     * تحميل الإعدادات من ConfigService
     */
    private void loadCurrentSettings() {
        log.debug("Loading current settings");
        String openAIKey = configService.get(ConfigService.OPENAI_API_KEY);
        String geminiKey = configService.get(ConfigService.GEMINI_API_KEY);
        String unsplashKey = configService.get(ConfigService.UNSPLASH_API_KEY);

        if (openAIKey != null) txtOpenAIKey.setText(openAIKey);
        if (geminiKey != null) txtGeminiKey.setText(geminiKey);
        if (unsplashKey != null) txtUnsplashKey.setText(unsplashKey);
        String claudeKey = configService.get(ConfigService.CLAUDE_API_KEY);
        if (claudeKey != null) txtClaudeKey.setText(claudeKey);
        String telegramToken = configService.get(ConfigService.TELEGRAM_BOT_TOKEN);
        if (telegramToken != null) txtTelegramToken.setText(telegramToken);
        String facebookToken = configService.get(ConfigService.FACEBOOK_PAGE_TOKEN);
        if (facebookToken != null) txtFacebookToken.setText(facebookToken);

        txtOpenAIModel.setText(configService.getOpenAIModel());
        String geminiModel = configService.getGeminiModel();
        txtGeminiModel.setText(geminiModel != null ? geminiModel : "");
        txtClaudeModel.setText(configService.getClaudeModel());
        txtImageModel.setText(configService.getOpenAIImageModel());
        select(cmbImageQuality, configService.getOpenAIImageQuality());

        String aiProvider = configService.getAIProvider();
        switch (aiProvider) {
            case "gemini" -> rbGemini.setSelected(true);
            case "claude" -> rbClaude.setSelected(true);
            default -> rbOpenAI.setSelected(true);
        }
        log.debug("AI Provider: {}", aiProvider);

        select(cmbArticleLength, configService.getArticleLength());
        chkArticleFaq.setSelected(configService.isArticleFaq());
        chkSeoAi.setSelected(configService.isSeoAiEnabled());
        chkQualityCheck.setSelected(configService.isQualityCheckEnabled());
        chkInternalLinks.setSelected(configService.isInternalLinksEnabled());
        txtArticleInstructions.setText(configService.getArticleInstructions());
        chkPublishAsDraft.setSelected(ConfigService.POST_STATUS_DRAFT.equals(configService.getPostStatus()));

        String imageProvider = configService.getImageProvider();
        if ("dalle".equals(imageProvider)) {
            rbDallE.setSelected(true);
            log.debug("Image Provider: DALL-E");
        } else {
            rbUnsplash.setSelected(true);
            log.debug("Image Provider: Unsplash");
        }

        // تحميل إعدادات النشر
        int delay = configService.getPublishDelay();
        sliderDelay.setValue(delay);
        updateDelayLabel(delay);
        log.debug("Publish Delay: {} seconds", delay);

        String slugType = configService.getSlugType();
        switch (slugType) {
            case "custom":
                rbSlugCustom.setSelected(true);
                log.debug("Slug: Custom (Arabic)");
                break;
            case "english":
                rbSlugEnglish.setSelected(true);
                lblSlugNote.setVisible(true);
                log.debug("Slug: English (SEO)");
                break;
            default:
                rbSlugAuto.setSelected(true);
                log.debug("Slug: Auto (Blogger)");
                break;
        }

        String titleLang = configService.getTitleLanguage();
        if ("english".equals(titleLang)) {
            rbTitleEnglish.setSelected(true);
        } else {
            rbTitleArabic.setSelected(true);
        }

        String contentLang = configService.getContentLanguage();
        if ("english".equals(contentLang)) {
            rbContentEnglish.setSelected(true);
        } else {
            rbContentArabic.setSelected(true);
        }

        chkSkipDuplicates.setSelected(configService.isSkipDuplicates());

        log.info("Settings loaded successfully");
    }

    /**
     * يختار الخيار صاحب القيمة المخزَّنة، أو الأوسط (القيمة الافتراضية في القائمتين)
     * إن لم توجد قيمة مطابقة.
     */
    private static void select(ComboBox<Choice> combo, String value) {
        combo.setValue(combo.getItems().stream()
                .filter(c -> c.value().equals(value))
                .findFirst()
                .orElse(combo.getItems().get(1)));
    }

    /**
     * حفظ الإعدادات
     */
    @FXML
    public void handleSave() {
        log.info("Saving settings");
        try {
            // getText() يعيد النقاط المخفية؛ getPassword() هو الذي يعيد المفتاح الحقيقي
            configService.set(ConfigService.OPENAI_API_KEY, txtOpenAIKey.getPassword().trim());
            configService.set(ConfigService.GEMINI_API_KEY, txtGeminiKey.getPassword().trim());
            configService.set(ConfigService.UNSPLASH_API_KEY, txtUnsplashKey.getPassword().trim());
            configService.set(ConfigService.CLAUDE_API_KEY, txtClaudeKey.getPassword().trim());
            configService.set(ConfigService.TELEGRAM_BOT_TOKEN, txtTelegramToken.getPassword().trim());
            configService.set(ConfigService.FACEBOOK_PAGE_TOKEN, txtFacebookToken.getPassword().trim());

            // حقل فارغ يحذف الإعداد فيعود النموذج الافتراضي (أو الاكتشاف التلقائي لـ Gemini)
            configService.set(ConfigService.OPENAI_MODEL, txtOpenAIModel.getText().trim());
            configService.set(ConfigService.GEMINI_MODEL, txtGeminiModel.getText().trim());
            configService.set(ConfigService.CLAUDE_MODEL, txtClaudeModel.getText().trim());
            configService.set(ConfigService.OPENAI_IMAGE_MODEL, txtImageModel.getText().trim());
            Choice imageQuality = cmbImageQuality.getValue();
            configService.set(ConfigService.OPENAI_IMAGE_QUALITY, imageQuality != null ? imageQuality.value() : null);

            String aiProvider = rbGemini.isSelected() ? "gemini"
                    : rbClaude.isSelected() ? "claude" : "openai";
            configService.set(ConfigService.AI_PROVIDER, aiProvider);
            log.debug("AI Provider set to: {}", aiProvider);

            String imageProvider = rbUnsplash.isSelected() ? "unsplash" : "dalle";
            configService.set(ConfigService.IMAGE_PROVIDER, imageProvider);
            log.debug("Image Provider set to: {}", imageProvider);

            // حفظ إعدادات النشر
            int delay = (int) sliderDelay.getValue();
            configService.setPublishDelay(delay);
            log.debug("Publish Delay set to: {} seconds", delay);

            String slugType = rbSlugAuto.isSelected() ? "auto" :
                              rbSlugCustom.isSelected() ? "custom" : "english";
            configService.setSlugType(slugType);
            log.debug("Slug Type set to: {}", slugType);

            String titleLang = rbTitleEnglish.isSelected() ? "english" : "arabic";
            configService.setTitleLanguage(titleLang);
            log.debug("Title Language set to: {}", titleLang);

            String contentLang = rbContentEnglish.isSelected() ? "english" : "arabic";
            configService.setContentLanguage(contentLang);
            log.debug("Content Language set to: {}", contentLang);

            Choice length = cmbArticleLength.getValue();
            configService.set(ConfigService.ARTICLE_LENGTH, length != null ? length.value() : null);
            configService.setArticleFaq(chkArticleFaq.isSelected());
            configService.setSeoAiEnabled(chkSeoAi.isSelected());
            configService.setQualityCheckEnabled(chkQualityCheck.isSelected());
            configService.setInternalLinksEnabled(chkInternalLinks.isSelected());
            configService.set(ConfigService.ARTICLE_INSTRUCTIONS, txtArticleInstructions.getText());
            configService.set(ConfigService.POST_STATUS, chkPublishAsDraft.isSelected()
                    ? ConfigService.POST_STATUS_DRAFT : ConfigService.POST_STATUS_PUBLISH);

            configService.setSkipDuplicates(chkSkipDuplicates.isSelected());
            log.debug("Skip duplicates set to: {}", chkSkipDuplicates.isSelected());

            configService.saveConfig();
            log.info("Settings saved successfully");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(bundle.getString("settings.save.success"));
            alert.setHeaderText(null);
            alert.setContentText(bundle.getString("settings.save.success"));
            alert.showAndWait();

            closeWindow();

        } catch (IOException e) {
            log.error("Failed to save settings", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(bundle.getString("settings.save.error"));
            alert.setHeaderText(bundle.getString("settings.save.error"));
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * إلغاء وإغلاق النافذة
     */
    @FXML
    public void handleCancel() {
        closeWindow();
    }

    /**
     * إغلاق النافذة
     */
    private void closeWindow() {
        WorkspaceTabs.close(btnCancel);
    }
}
