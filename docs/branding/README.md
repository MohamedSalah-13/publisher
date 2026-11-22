# App icon

`app-icon-512.png` is the master render of the app icon (an article page with a paper plane taking off: write, then publish).

`IconRender.java` draws it as vector shapes on a 512 grid, so every size is rendered crisply instead of being downscaled. It also holds the two rejected concepts (B: pen nib, C: article stack), kept for reference.

To regenerate after changing the design, from the project root:

```bash
java --module-path "<javafx-base>;<javafx-graphics>;<javafx-controls>" --add-modules javafx.controls -cp "<app-icons jar>" docs/branding/IconRender.java write .
```

The JavaFX jars and the app-icons jar are in `~/.m2/repository` after any build (`org/openjfx/...` and `com/github/MohamedSalah-13/app-icons/v0.1.0/app-icons-v0.1.0.jar`); each JavaFX module needs its plain jar and its platform jar (`-win`, `-linux`, `-mac`). On Linux and macOS the separator is `:`.

This writes every file the program ships, straight into place:

- `src/main/resources/com/hamzatech/publisher/images/app-icon-<size>.png`, 16 to 256: `util.AppIcons` gives the window every size so Windows picks the sharpest one for each place.
- `tool.ico`, for Windows packaging.
- `tool.icns`, for the macOS app and disk image.
- `docs/branding/app-icon-512.png`, for the Linux package.

JavaFX does the drawing; the shared [app-icons](https://github.com/MohamedSalah-13/app-icons) library writes the files. It is a test-scope dependency in `pom.xml`, so the installer does not carry it.

To look at a candidate without touching the program's files: `IconRender.java A <out-dir>` (or `B`, `C`) writes `icon_16.png` … `icon_512.png` there, and `IconRender.java sheet <out-dir>` writes the three side by side as `icon_options.png`.
