package com.hamzatech.publisher.controller;

import com.hamzatech.publisher.model.EffectiveSettings;
import com.hamzatech.publisher.model.PublishTarget;
import com.hamzatech.publisher.service.AIService;
import com.hamzatech.publisher.service.DuplicateGuard;
import com.hamzatech.publisher.service.ExcelService;
import com.hamzatech.publisher.service.PublishingProfileService;
import com.hamzatech.publisher.service.TitleIdeasService;
import com.hamzatech.publisher.util.AppIcons;
import com.hamzatech.publisher.util.LocaleManager;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.extern.log4j.Log4j2;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * نافذة توليد عناوين من موضوع.
 * <p>
 * العناوين المختارة تُحفظ ملف Excel عاديًّا ويُختار في اللوحة كأن المستخدم رفعه،
 * فيمر في المعاينة والجدولة والطابور والنشر دون أي مسار جديد.
 * <p>
 * كلاس مستقل لا جزء من {@link DashboardController}، الذي تجاوز ألف سطر.
 */
@Log4j2
final class TitleGeneratorDialog {

    private final ResourceBundle bundle;
    private final PublishTarget target;
    private final Consumer<File> onFileReady;
    private final Function<Throwable, String> errorText;

    private final Stage stage = new Stage();
    private final TextField txtTopic = new TextField();
    private final Spinner<Integer> spnCount = new Spinner<>(TitleIdeasService.MIN_COUNT, TitleIdeasService.MAX_COUNT, 10);
    private final ComboBox<String> cmbLanguage = new ComboBox<>();
    private final Button btnGenerate;
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label lblStatus = new Label();
    private final VBox rows = new VBox(6);
    private final Label lblSelected = new Label();
    private final Button btnSave;
    private final List<Row> rowList = new ArrayList<>();

    /** عنوان واحد: قابل للاختيار والتعديل قبل الحفظ. */
    private record Row(CheckBox check, TextField text) {
    }

    /**
     * @param target      الوجهة المختارة في اللوحة، أو {@code null}؛ منها يُؤخذ مزود
     *                    الذكاء الاصطناعي وتُعلَّم العناوين المنشورة عليها من قبل
     * @param onFileReady يُستدعى بالملف المحفوظ ليختاره اللوحة
     * @param errorText   تحويل الاستثناء إلى رسالة مفهومة، كما في بقية اللوحة
     */
    TitleGeneratorDialog(Window owner, PublishTarget target, Consumer<File> onFileReady,
                         Function<Throwable, String> errorText) {
        this.bundle = LocaleManager.getInstance().getResourceBundle();
        this.target = target;
        this.onFileReady = onFileReady;
        this.errorText = errorText;

        txtTopic.setPromptText(bundle.getString("titles.topic.prompt"));
        HBox.setHgrow(txtTopic, Priority.ALWAYS);
        spnCount.setEditable(true);
        spnCount.setPrefWidth(90);

        String arabic = bundle.getString("settings.language.arabic");
        String english = bundle.getString("settings.language.english");
        cmbLanguage.getItems().addAll(arabic, english);
        EffectiveSettings settings = PublishingProfileService.getInstance().resolve(target);
        cmbLanguage.setValue("english".equals(settings.titleLanguage()) && "english".equals(settings.contentLanguage())
                ? english : arabic);

        btnGenerate = new Button(bundle.getString("titles.generate.button"), new FontIcon("fth-zap"));
        btnGenerate.getStyleClass().add("accent");
        btnGenerate.setDefaultButton(true);
        btnGenerate.disableProperty().bind(txtTopic.textProperty().isEmpty());
        btnGenerate.setOnAction(e -> generate());

        progress.setMaxSize(20, 20);
        progress.setVisible(false);
        lblStatus.getStyleClass().add("text-muted");
        lblStatus.setWrapText(true);

        HBox inputs = new HBox(10, txtTopic, new Label(bundle.getString("titles.count")), spnCount,
                cmbLanguage, btnGenerate, progress);
        inputs.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label(bundle.getString("titles.hint"));
        hint.getStyleClass().addAll("text-muted", "text-small");
        hint.setWrapText(true);

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        rows.setPadding(new Insets(4));

        VBox content = new VBox(10, inputs, hint, lblStatus, scroll);
        content.setPadding(new Insets(16));

        Button btnAll = new Button(bundle.getString("titles.select.all"));
        btnAll.getStyleClass().add("flat");
        btnAll.setOnAction(e -> setAllSelected(true));
        Button btnNone = new Button(bundle.getString("titles.select.none"));
        btnNone.getStyleClass().add("flat");
        btnNone.setOnAction(e -> setAllSelected(false));

        Button btnCancel = new Button(bundle.getString("categories.cancel.button"));
        btnCancel.setOnAction(e -> stage.close());
        btnSave = new Button(bundle.getString("titles.save.button"), new FontIcon("fth-save"));
        btnSave.getStyleClass().add("success");
        btnSave.setOnAction(e -> save());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, btnAll, btnNone, lblSelected, spacer, btnCancel, btnSave);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(10, 16, 14, 16));

        BorderPane root = new BorderPane(content);
        root.setBottom(actions);

        Scene scene = new Scene(root, 760, 600);
        LocaleManager.getInstance().applyOrientation(scene);
        stage.setScene(scene);
        stage.setTitle(bundle.getString("titles.title"));
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        AppIcons.applyTo(stage);
        updateSelectedCount();
    }

    void show() {
        stage.show();
        txtTopic.requestFocus();
    }

    private void generate() {
        String topic = txtTopic.getText().trim();
        int count = spnCount.getValue();
        String language = cmbLanguage.getValue().equals(bundle.getString("settings.language.english"))
                ? "english" : "arabic";
        String provider = PublishingProfileService.getInstance().resolve(target).aiProvider();

        setBusy(true);
        lblStatus.setText(bundle.getString("titles.generating"));

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return new AIService(provider).generateTitles(topic, count, language);
            }
        };
        task.setOnSucceeded(e -> {
            showTitles(task.getValue());
            setBusy(false);
        });
        task.setOnFailed(e -> {
            log.error("Failed to generate titles for '{}'", topic, task.getException());
            lblStatus.setText(MessageFormat.format(bundle.getString("titles.error"),
                    errorText.apply(task.getException())));
            setBusy(false);
        });
        Thread thread = new Thread(task, "generate-titles");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * يعرض العناوين مختارةً كلها، إلا ما نُشر من قبل على الوجهة المختارة: يبقى
     * ظاهرًا للعلم لكن غير مختار، فلا يُنشر مكررًا بالخطأ.
     */
    private void showTitles(List<String> titles) {
        DuplicateGuard guard = target != null ? DuplicateGuard.fromPublishLog() : null;
        rows.getChildren().clear();
        rowList.clear();
        int published = 0;
        for (String title : titles) {
            boolean already = guard != null && guard.alreadyPublished(title, target);
            if (already) {
                published++;
            }
            CheckBox check = new CheckBox();
            check.setSelected(!already);
            check.selectedProperty().addListener((obs, was, now) -> updateSelectedCount());
            TextField text = new TextField(title);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(8, check, text);
            row.setAlignment(Pos.CENTER_LEFT);
            if (already) {
                Label tag = new Label(bundle.getString("titles.already.published"));
                tag.getStyleClass().addAll("warning", "text-small");
                row.getChildren().add(tag);
            }
            rows.getChildren().add(row);
            rowList.add(new Row(check, text));
        }
        lblStatus.setText(published == 0
                ? MessageFormat.format(bundle.getString("titles.generated"), titles.size())
                : MessageFormat.format(bundle.getString("titles.generated.with.published"), titles.size(), published));
        updateSelectedCount();
    }

    private void save() {
        List<String> chosen = rowList.stream()
                .filter(r -> r.check().isSelected())
                .map(r -> r.text().getText().trim())
                .filter(t -> !t.isEmpty())
                .toList();
        if (chosen.isEmpty()) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        chooser.setInitialFileName(fileNameFor(txtTopic.getText()));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        if (!file.getName().toLowerCase().endsWith(".xlsx")) {
            file = new File(file.getParentFile(), file.getName() + ".xlsx");
        }

        try {
            new ExcelService().writeTitles(file, chosen);
            log.info("Saved {} generated titles to {}", chosen.size(), file);
            onFileReady.accept(file);
            stage.close();
        } catch (Exception ex) {
            log.error("Failed to save generated titles", ex);
            lblStatus.setText(MessageFormat.format(bundle.getString("titles.save.error"), errorText.apply(ex)));
        }
    }

    /** اسم ملف آمن من الموضوع؛ الحروف العربية مقبولة في أسماء ملفات Windows. */
    static String fileNameFor(String topic) {
        String base = topic == null ? "" : topic.replaceAll("[\\\\/:*?\"<>|]+", " ")
                .trim()
                .replaceAll("\\s+", "-");
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        return (base.isEmpty() ? "titles" : base) + ".xlsx";
    }

    private void setAllSelected(boolean selected) {
        rowList.forEach(r -> r.check().setSelected(selected));
    }

    private void updateSelectedCount() {
        long selected = rowList.stream().filter(r -> r.check().isSelected()).count();
        lblSelected.setText(MessageFormat.format(bundle.getString("titles.selected"), selected));
        btnSave.setDisable(selected == 0);
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        txtTopic.setDisable(busy);
        spnCount.setDisable(busy);
        cmbLanguage.setDisable(busy);
        btnGenerate.disableProperty().unbind();
        if (busy) {
            btnGenerate.setDisable(true);
        } else {
            btnGenerate.disableProperty().bind(txtTopic.textProperty().isEmpty());
        }
    }
}
