package com.hamzatech.publisher.controller;

import com.hamzatech.publisher.util.LocaleManager;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * يفتح شاشات التطبيق (الإعدادات، المواقع، التصنيفات، السجل، الطابور) تبويباتٍ
 * داخل النافذة الرئيسية بدل نوافذ منبثقة.
 * <p>
 * التبويب يكبر ويصغر مع النافذة، ويأخذ سمتها الداكنة كاملة، ولا يحجب بقية
 * اللوحة. فتح شاشة مفتوحة أصلًا ينتقل إلى تبويبها بدل فتح نسخة ثانية.
 * <p>
 * الشاشات نفسها لا تعرف أين تُعرض: تُغلق نفسها بـ {@link #close(Node)}، الذي
 * يغلق تبويبها إن كانت في تبويب، ونافذتها إن كانت في نافذة.
 */
final class WorkspaceTabs {

    /** مفتاح في خصائص جذر الشاشة يحمل تبويبها، ليجده {@link #close(Node)}. */
    private static final String TAB_KEY = "publisher.workspace.tab";

    private final TabPane pane;
    private final Map<String, Tab> open = new HashMap<>();

    WorkspaceTabs(TabPane pane) {
        this.pane = pane;
    }

    /**
     * @param onLeave  يُستدعى كلما غادر المستخدم التبويب أو أغلقه — لتحديث ما في
     *                 اللوحة مما قد تكون الشاشة غيّرته (المواقع، التصنيفات)
     * @param onClosed يُستدعى بمتحكم الشاشة عند إغلاق تبويبها (بعد {@code onLeave})
     */
    <C> void open(String fxmlPath, String titleKey, String iconLiteral,
                  Runnable onLeave, Consumer<C> onClosed) throws IOException {
        Tab existing = open.get(fxmlPath);
        if (existing != null) {
            pane.getSelectionModel().select(existing);
            return;
        }

        ResourceBundle bundle = LocaleManager.getInstance().getResourceBundle();
        FXMLLoader loader = new FXMLLoader(WorkspaceTabs.class.getResource(fxmlPath), bundle);
        Parent root = loader.load();
        C controller = loader.getController();

        Tab tab = new Tab(bundle.getString(titleKey), root);
        tab.setGraphic(new FontIcon(iconLiteral));
        tab.setClosable(true);
        root.getProperties().put(TAB_KEY, tab);

        tab.setOnSelectionChanged(e -> {
            if (!tab.isSelected() && tab.getTabPane() != null && onLeave != null) {
                onLeave.run();
            }
        });
        tab.setOnClosed(e -> {
            open.remove(fxmlPath);
            if (onLeave != null) {
                onLeave.run();
            }
            if (onClosed != null) {
                onClosed.accept(controller);
            }
        });

        open.put(fxmlPath, tab);
        pane.getTabs().add(tab);
        pane.getSelectionModel().select(tab);
    }

    /**
     * يغلق الشاشة التي تحتوي العقدة: تبويبها إن كانت في تبويب، وإلا نافذتها.
     * <p>
     * كانت الشاشات تغلق {@code getScene().getWindow()} مباشرة؛ داخل تبويب كان
     * ذلك سيغلق النافذة الرئيسية للتطبيق كله.
     */
    static void close(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n.getProperties().get(TAB_KEY) instanceof Tab tab) {
                closeTab(tab);
                return;
            }
        }
        if (node.getScene() != null) {
            Window window = node.getScene().getWindow();
            if (window instanceof Stage stage) {
                stage.close();
            }
        }
    }

    /**
     * إزالة التبويب برمجيًا لا تطلق {@code onClosed} — ذلك لإغلاق المستخدم من
     * زر التبويب فقط — فيُطلق هنا يدويًا حتى يتصرف الإغلاقان بالطريقة نفسها.
     */
    private static void closeTab(Tab tab) {
        TabPane tabPane = tab.getTabPane();
        if (tabPane == null) {
            return;
        }
        tabPane.getTabs().remove(tab);
        EventHandler<Event> handler = tab.getOnClosed();
        if (handler != null) {
            handler.handle(new Event(Tab.CLOSED_EVENT));
        }
    }
}
