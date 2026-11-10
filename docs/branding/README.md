# App icon

`app-icon-512.png` is the master render of the app icon (an article page with a paper plane taking off: write, then publish).

`IconRender.java` draws it as vector shapes on a 512 grid, so every size is rendered crisply instead of being downscaled. It also holds the two rejected concepts (B: pen nib, C: article stack), kept for reference.

To regenerate after changing the design:

```bash
java --module-path "<javafx-base>;<javafx-graphics>;<javafx-controls>" --add-modules javafx.controls IconRender.java A <out-dir>
```

This writes `icon_16.png` … `icon_512.png`. Copy 16–256 to `src/main/resources/com/hamzatech/publisher/images/app-icon-<size>.png`, and pack the same sizes into `tool.ico` (PNG-compressed ICO entries) for Windows packaging. `util.AppIcons` gives the window every size so Windows picks the sharpest one for each place.
