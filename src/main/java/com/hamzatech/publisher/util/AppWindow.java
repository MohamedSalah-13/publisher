package com.hamzatech.publisher.util;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Keeps JavaFX 25's preview window API in one place. */
@SuppressWarnings("deprecation")
public final class AppWindow {
    /** Must run before the JavaFX toolkit caches preview settings. */
    public static void enablePreview() {
        System.setProperty("javafx.enablePreview", "true");
    }

    private AppWindow() { }

    public static void initialize(Stage stage) {
        stage.initStyle(Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW)
                ? StageStyle.EXTENDED : StageStyle.DECORATED);
        HeaderBar.setPrefButtonHeight(stage, 60);
        stage.setMinWidth(1000);
        stage.setMinHeight(720);
    }

    public static Scene createScene(Parent content) {
        Node toolbar;
        if (content instanceof BorderPane dashboard
                && content.getStyleClass().contains("publisher-dashboard")) {
            toolbar = dashboard.getTop();
            dashboard.setTop(null);
        } else {
            Label title = new Label(LocaleManager.getInstance().getString("app.title"));
            title.getStyleClass().add("title-4");
            toolbar = new HBox(12, AppIcons.logo(28), title);
            ((HBox) toolbar).setAlignment(Pos.CENTER_LEFT);
            toolbar.getStyleClass().add("window-brand");
        }
        HeaderBar header = new HeaderBar();
        header.getStyleClass().add("window-header");
        header.setCenter(toolbar);
        HeaderBar.setAlignment(toolbar, Pos.CENTER_LEFT);
        HeaderBar.setDragType(toolbar, HeaderDragType.DRAGGABLE_SUBTREE);
        keepControlsInteractive(toolbar);
        BorderPane shell = new BorderPane(content);
        shell.setTop(header);
        shell.getStyleClass().add("app-shell");
        shell.getStylesheets().add(AppWindow.class.getResource("/com/hamzatech/publisher/view/styles.css").toExternalForm());
        Scene scene = new Scene(shell);
        var darkMode = ThemeManager.getInstance().darkModeProperty();

        // أزرار التصغير/التكبير/الإغلاق في نافذة EXTENDED يرسمها JavaFX، ويختار لون
        // رموزها (فاتح/داكن) من لون خلفية المشهد (fill) — لا من السمة ولا من نظام
        // الألوان (HeaderButtonOverlay.isDarkBackground). فاللون يتبع السمة، لكن في
        // الدورة التالية لا مع تبديل ورقة الأنماط العامة نفسه: تغييرهما معًا كان
        // يترك الأزرار بلا أنماط فتختفي بعد أول تبديل (جُرِّب).
        // خلفية النافذة نفسها من CSS السمة (.app-shell)، فلا يظهر أثر للتأخير.
        scene.setFill(headerFill(darkMode.get()));
        // المستمع ضعيف حتى لا يُبقي المشهد القديم حيًّا بعد استبداله عند تغيير
        // اللغة؛ مرجعه القوي محفوظ في المشهد نفسه
        ChangeListener<Boolean> fillListener = (obs, was, dark) ->
                Platform.runLater(() -> scene.setFill(headerFill(dark)));
        scene.getProperties().put(ChangeListener.class, fillListener);
        darkMode.addListener(new WeakChangeListener<>(fillListener));
        LocaleManager.getInstance().applyOrientation(scene);
        return scene;
    }

    private static Color headerFill(boolean dark) {
        return Color.web(dark ? AppColors.HEADER_DARK : AppColors.HEADER_LIGHT);
    }

    private static void keepControlsInteractive(Node node) {
        if (node instanceof ButtonBase || node instanceof ComboBoxBase<?>) {
            HeaderBar.setDragType(node, HeaderDragType.NONE);
        } else if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(AppWindow::keepControlsInteractive);
        }
    }
}