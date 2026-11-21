import com.hamza.icons.IconDrawing;
import com.hamza.icons.IconSet;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Draws the app icon, and the two rejected candidates, on a 512 grid and renders them at any size.
 * JavaFX draws; the files are written by the app-icons library the programs share
 * (github.com/MohamedSalah-13/app-icons). How to run it is in this folder's README.
 */
public class IconRender extends Application {

    /** The sizes a candidate is previewed at; the program's own files take the library's usual sizes. */
    static final int[] PREVIEW_SIZES = {16, 24, 32, 48, 64, 128, 256, 512};
    static final Color BLUE = Color.web("#2F6DB5");
    static final Color ORANGE = Color.web("#FF8A1F");
    static final Color ORANGE_DARK = Color.web("#D9640A");

    static LinearGradient grad(String a, String b) {
        return new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(a)), new Stop(1, Color.web(b)));
    }

    static Rectangle tile(LinearGradient fill) {
        Rectangle r = new Rectangle(16, 16, 480, 480);
        r.setArcWidth(120);
        r.setArcHeight(120);
        r.setFill(fill);
        return r;
    }

    static SVGPath path(String d, Color fill) {
        SVGPath p = new SVGPath();
        p.setContent(d);
        p.setFill(fill);
        return p;
    }

    static Rectangle bar(double x, double y, double w, double h, Color c) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setArcWidth(h);
        r.setArcHeight(h);
        r.setFill(c);
        return r;
    }

    static Rectangle page(double x, double y, double w, double h, Color c) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setArcWidth(40);
        r.setArcHeight(40);
        r.setFill(c);
        return r;
    }

    static DropShadow shadow() {
        DropShadow s = new DropShadow(18, 0, 8, Color.rgb(0, 0, 0, 0.28));
        return s;
    }

    /** A: an article page with a paper plane taking off — "write, then publish". */
    static Group optionA() {
        Group g = new Group(tile(grad("#3B82F6", "#1E3A8A")));
        Group doc = new Group();
        doc.getChildren().add(page(104, 150, 230, 286, Color.WHITE));
        Color line = Color.web("#2F6DB5", 0.55);
        // right-aligned lines, like Arabic text
        doc.getChildren().addAll(
                bar(140, 200, 160, 22, Color.web("#1E3A8A")),
                bar(150, 252, 150, 16, line),
                bar(180, 288, 120, 16, line),
                bar(140, 324, 160, 16, line),
                bar(200, 360, 100, 16, line));
        doc.setEffect(shadow());
        Group plane = new Group(
                path("M440 78 L236 176 L322 206 Z", ORANGE),
                path("M440 78 L322 206 L352 300 Z", Color.web("#FFB061")),
                path("M322 206 L352 300 L372 238 Z", ORANGE_DARK));
        plane.setEffect(shadow());
        g.getChildren().addAll(doc, plane);
        return g;
    }

    /** B: a pen nib with an AI sparkle — "AI writes it". */
    static Group optionB() {
        Group g = new Group(tile(grad("#FF9F43", "#E8452C")));
        Group nib = new Group(
                path("M256 96 C336 160 372 246 346 330 L256 440 L166 330 C140 246 176 160 256 96 Z", Color.WHITE),
                path("M250 300 L262 300 L262 440 L250 440 Z", Color.web("#F06A35")));
        Circle hole = new Circle(256, 290, 24, Color.web("#F06A35"));
        nib.getChildren().add(hole);
        nib.getTransforms().add(new Rotate(-35, 256, 268));
        nib.setEffect(shadow());
        SVGPath big = path("M392 70 Q399 124 452 131 Q399 138 392 192 Q385 138 332 131 Q385 124 392 70 Z", Color.WHITE);
        SVGPath small = path("M126 330 Q130 362 162 366 Q130 370 126 402 Q122 370 90 366 Q122 362 126 330 Z", Color.web("#FFFFFF", 0.85));
        g.getChildren().addAll(nib, big, small);
        return g;
    }

    /** C: a stack of articles with a publish arrow — "batch publishing". */
    static Group optionC() {
        Group g = new Group(tile(grad("#1F4E9A", "#0B1F45")));
        Rectangle back = page(150, 120, 210, 270, Color.web("#7FA8E6"));
        back.getTransforms().add(new Rotate(-14, 255, 255));
        Rectangle mid = page(140, 130, 210, 270, Color.web("#C4D7F5"));
        mid.getTransforms().add(new Rotate(-6, 245, 265));
        Group front = new Group(page(120, 150, 210, 270, Color.WHITE),
                bar(150, 196, 150, 22, Color.web("#1F4E9A")),
                bar(160, 246, 140, 16, Color.web("#1F4E9A", 0.45)),
                bar(185, 280, 115, 16, Color.web("#1F4E9A", 0.45)),
                bar(150, 314, 150, 16, Color.web("#1F4E9A", 0.45)));
        Group badge = new Group(new Circle(366, 372, 86, ORANGE),
                path("M366 316 L420 374 L388 374 L388 428 L344 428 L344 374 L312 374 Z", Color.WHITE));
        Group stack = new Group(back, mid, front);
        stack.setEffect(shadow());
        badge.setEffect(shadow());
        g.getChildren().addAll(stack, badge);
        return g;
    }

    static WritableImage render(Supplier<Group> icon, int size) {
        Group g = icon.get();
        g.getTransforms().add(new Scale(size / 512.0, size / 512.0, 0, 0));
        Group holder = new Group(new Rectangle(0, 0, size, size) {{ setFill(Color.TRANSPARENT); }}, g);
        holder.setClip(new Rectangle(0, 0, size, size));
        new Scene(holder, size, size, Color.TRANSPARENT);
        SnapshotParameters p = new SnapshotParameters();
        p.setFill(Color.TRANSPARENT);
        return holder.snapshot(p, null);
    }

    static BufferedImage buffered(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
        return bi;
    }

    /** A candidate as the library takes it: each size is JavaFX's own picture of it, pixel for pixel. */
    static IconDrawing drawing(Supplier<Group> icon) {
        return new IconDrawing() {
            @Override
            public void draw(Graphics2D g, int size) {
                g.drawImage(render(size), 0, 0, null);
            }

            @Override
            public BufferedImage render(int size) {
                return buffered(IconRender.render(icon, size));
            }
        };
    }

    @Override
    public void start(Stage stage) throws Exception {
        String mode = getParameters().getRaw().get(0);
        Path target = Path.of(getParameters().getRaw().get(1));
        List<Supplier<Group>> options = List.of(IconRender::optionA, IconRender::optionB, IconRender::optionC);
        if (mode.equals("write")) {
            // The program's files, from the chosen design (A). target is the project root.
            IconSet.of(drawing(IconRender::optionA))
                    .pngs("src/main/resources/com/hamzatech/publisher/images/app-icon-%d.png") // the windows (util.AppIcons)
                    .ico("tool.ico")                                                            // the Windows launcher and installer
                    .png("docs/branding/app-icon-512.png", 512)                                 // the Linux package, and the master render
                    .write(target);
        } else if (mode.equals("sheet")) {
            HBox row = new HBox(40);
            row.setPadding(new Insets(30));
            String[] names = {"A", "B", "C"};
            for (int i = 0; i < options.size(); i++) {
                Supplier<Group> o = options.get(i);
                HBox small = new HBox(14, new javafx.scene.image.ImageView(render(o, 48)),
                        new javafx.scene.image.ImageView(render(o, 32)),
                        new javafx.scene.image.ImageView(render(o, 16)));
                small.setAlignment(Pos.BOTTOM_LEFT);
                Label l = new Label(names[i]);
                l.setStyle("-fx-font-size: 34px; -fx-font-weight: bold;");
                VBox col = new VBox(16, l, new javafx.scene.image.ImageView(render(o, 256)), small);
                col.setAlignment(Pos.TOP_CENTER);
                row.getChildren().add(col);
            }
            row.setStyle("-fx-background-color: #F3F4F6;");
            new Scene(row);
            ImageIO.write(buffered(row.snapshot(null, null)), "png", new File(target.toFile(), "icon_options.png"));
        } else {
            // One candidate as loose PNGs, to look at: icon_16.png ... icon_512.png in target.
            int idx = "ABC".indexOf(mode);
            IconSet.of(drawing(options.get(idx))).pngs("icon_%d.png", PREVIEW_SIZES).write(target);
        }
        System.out.println("RENDER OK " + mode);
        Platform.exit();
    }

    /** {@code write <project root>}, {@code sheet <out dir>}, or {@code A|B|C <out dir>}. */
    public static void main(String[] args) {
        launch(args);
    }
}
