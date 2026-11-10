package com.hamzatech.publisher.controller;

import com.hamzatech.publisher.model.ArticleContent;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.service.ArticlePublishingService;
import com.hamzatech.publisher.util.AppIcons;
import com.hamzatech.publisher.util.LocaleManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.kordamp.ikonli.javafx.FontIcon;

import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 * نافذة معاينة مقال قبل نشره: تولّد المحتوى لعنوان من الملف وتعرضه مع معاينة SEO.
 * <p>
 * نُقلت من {@link DashboardController} كما هي؛ لا تنشر شيئًا، بل تستدعي
 * {@link ArticlePublishingService#generateContent} وحدها.
 */
@Log4j2
final class ArticlePreviewDialog {

    private final ResourceBundle bundle = LocaleManager.getInstance().getResourceBundle();
    private final PublishTarget target;
    private final Function<Throwable, String> errorText;

    private final Stage stage = new Stage();
    private final ComboBox<String> cmbTitle = new ComboBox<>();
    private final Button btnGenerate;
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label lblTitle = new Label();
    private final Label lblGenStatus = new Label();
    private final WebView webView = new WebView();
    private final VBox seoBox;
    private final Label lblSeoTitle = new Label();
    private final Label lblSeoDescription = new Label();
    private final Label lblSeoTags = new Label();

    /**
     * @param titles    عناوين الملف المختار؛ غير فارغة
     * @param target    الوجهة المختارة لتُطبَّق إعداداتها الخاصة، أو {@code null}
     *                  فتُستخدم الإعدادات العامة. تُلتقط عند الفتح: النافذة مشروطة
     *                  فلا يتغير الاختيار أثناءها، وقراءتها من خيط خلفي كانت وصولًا
     *                  إلى JavaFX من خارج خيط الواجهة
     * @param errorText تحويل الاستثناء إلى رسالة مفهومة، كما في بقية اللوحة
     */
    ArticlePreviewDialog(List<String> titles, PublishTarget target, Function<Throwable, String> errorText) {
        this.target = target;
        this.errorText = errorText;

        cmbTitle.getItems().addAll(titles);
        cmbTitle.getSelectionModel().selectFirst();
        cmbTitle.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cmbTitle, Priority.ALWAYS);

        btnGenerate = new Button(bundle.getString("dashboard.preview.generate.button"),
                new FontIcon("fth-refresh-cw"));
        btnGenerate.getStyleClass().add("accent");
        btnGenerate.setOnAction(e -> generate());

        progress.setMaxSize(20, 20);
        progress.setVisible(false);

        lblTitle.getStyleClass().add("title-3");
        lblTitle.setWrapText(true);
        lblGenStatus.getStyleClass().add("text-muted");
        VBox.setVgrow(webView, Priority.ALWAYS);

        // معاينة بيانات SEO بشكل نتيجة بحث: ما سيراه القارئ قبل أن يفتح المقال
        Label lblSeoHeader = new Label(bundle.getString("dashboard.preview.seo"));
        lblSeoHeader.getStyleClass().addAll("text-small", "text-muted");
        lblSeoTitle.getStyleClass().addAll("accent", "text-bold");
        lblSeoTitle.setWrapText(true);
        lblSeoDescription.setWrapText(true);
        lblSeoTags.getStyleClass().addAll("text-small", "text-muted");
        lblSeoTags.setWrapText(true);
        seoBox = new VBox(4, lblSeoHeader, lblSeoTitle, lblSeoDescription, lblSeoTags);
        seoBox.getStyleClass().add("surface-card");
        showSeo(false);

        HBox topBar = new HBox(10, cmbTitle, btnGenerate, progress);
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, topBar, lblGenStatus, lblTitle, seoBox, webView);
        root.setPadding(new Insets(15));

        stage.setTitle(bundle.getString("dashboard.preview.title"));
        stage.initModality(Modality.APPLICATION_MODAL);
        Scene scene = new Scene(root, 700, 680);
        LocaleManager.getInstance().applyOrientation(scene);
        stage.setScene(scene);
        AppIcons.applyTo(stage);
    }

    /** يفتح النافذة ويولّد معاينة العنوان الأول مباشرة. */
    void show() {
        stage.show();
        generate();
    }

    private void generate() {
        String title = cmbTitle.getValue();
        if (title == null) return;

        setBusy(true);
        lblGenStatus.setText(bundle.getString("dashboard.preview.generating"));
        lblTitle.setText("");
        webView.getEngine().loadContent("");
        showSeo(false);

        Task<ArticleContent> genTask = new Task<>() {
            @Override
            protected ArticleContent call() throws Exception {
                // target قد تكون null إذا لم تُختَر مدونة بعد؛ generateContent
                // تتعامل مع ذلك باستخدام الإعدادات العامة وحدها
                return ArticlePublishingService.generateContent(title, target,
                        stage -> Platform.runLater(() -> {
                            String key = DashboardController.stageMessageKey(stage);
                            if (key != null) {
                                lblGenStatus.setText(bundle.getString(key));
                            }
                        }));
            }
        };

        genTask.setOnSucceeded(e -> {
            ArticleContent content = genTask.getValue();
            lblTitle.setText(content.publishTitle());
            webView.getEngine().loadContent(content.contentHtml());
            lblSeoTitle.setText(content.publishTitle());
            lblSeoDescription.setText(content.seo().hasDescription()
                    ? content.seo().description() : bundle.getString("dashboard.preview.seo.none"));
            lblSeoTags.setText(content.seo().tags().isEmpty() ? ""
                    : "# " + String.join("  # ", content.seo().tags()));
            showSeo(true);
            lblGenStatus.setText("");
            setBusy(false);
        });

        genTask.setOnFailed(e -> {
            log.error("Failed to generate article preview", genTask.getException());
            lblGenStatus.setText(MessageFormat.format(
                    bundle.getString("dashboard.preview.error"), errorText.apply(genTask.getException())));
            setBusy(false);
        });

        Thread th = new Thread(genTask, "preview-generate");
        th.setDaemon(true);
        th.start();
    }

    private void setBusy(boolean busy) {
        btnGenerate.setDisable(busy);
        cmbTitle.setDisable(busy);
        progress.setVisible(busy);
    }

    private void showSeo(boolean visible) {
        seoBox.setVisible(visible);
        seoBox.setManaged(visible);
    }
}
