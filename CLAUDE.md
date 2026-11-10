# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A JavaFX 25 desktop app (requires JDK 25 — the enforcer plugin fails the build on anything older) (`com.hamzatech.publisher`) that auto-publishes articles to Blogger and/or WordPress blogs from a list of titles in an Excel file. For each title it: optionally translates the title to English, generates the article body via an AI provider (OpenAI, Gemini or Claude), fetches a header image (Unsplash or an OpenAI image model), then publishes to the selected target and logs the result.

## Commands

```bash
mvn clean install          # build
mvn javafx:run             # run the app (launches MainApp via the javafx-maven-plugin)
mvn test                   # run all unit tests
mvn test -Dtest=ConfigServiceTest              # run a single test class
mvn test -Dtest=ConfigServiceTest#getPublishDelay_defaultsToFiveWhenUnset  # run a single test method
mvn clean package           # build the shaded runnable jar (target/publisher-<version>.jar, entry point Launcher)
.\packaging\build-installer.ps1   # Windows installer with a bundled Java runtime (target/installer/), see README
```

Requires a `credentials.json` (Google OAuth Desktop client, downloaded from Google Cloud Console) in the project root before the Blogger login flow will work — see README.md for the full walkthrough of obtaining that plus OpenAI/Gemini/Unsplash keys. WordPress publishing instead uses per-site Application Passwords entered in-app (no OAuth file needed).

### Where app files live

All runtime files (`config.properties`, `blog_categories.json`, `label_mappings.json`, `publish_logs.json`, `wordpress_sites.json`, and the OAuth `tokens/` directory) live in the user data directory, resolved by `util.AppPaths` — `%LOCALAPPDATA%\HamzaPublisher` on Windows, `$XDG_DATA_HOME/hamza-publisher` elsewhere, overridable with the `publisher.data.dir` system property. Use `AppPaths.dataFile(name)` for any new persisted file rather than a relative `Paths.get(name)`; it also moves a file left over in the working directory from older versions exactly once. `credentials.json` is the exception — it stays a user-supplied input file with its own search order in `GoogleCredentialsFile.locate` (the data directory via `AppPaths.dataDir()`, then the JAR directory, then the working directory; `BloggerService` falls back to resources after that). The data directory comes first because it is where the setup wizard copies a file the user picked, and that explicit choice has to beat a file bundled into the installer next to the JAR. Never resolve it with `AppPaths.dataFile`: that *moves* a working-directory file into the data directory, which would take the project's own `credentials.json` away.

### Secrets are encrypted at rest

API keys (`ConfigService`) and WordPress application passwords (`WordPressSiteService`) go through `util.SecretStore`, which uses Windows DPAPI via JNA. Values keep their plain form in memory — the app needs them to call the APIs — and are encrypted only at the file boundary, so `get`/`set` semantics are unchanged for callers. Stored values carry a `dpapi:` prefix; anything without it is treated as a legacy plain value and read as-is, which is what makes migration automatic. `SecretStore.reveal` returns `null` when decryption fails (file copied from another machine or Windows account); callers must drop the value and ask the user to re-enter it rather than pass it on.

### Tests run in isolated directories

The services above are singletons holding state for the whole JVM/test run — clean up what you add (see the `@BeforeEach`/`@AfterEach` patterns in the existing `*ServiceTest` classes). `maven-surefire-plugin` sets **two** separate directories: `workingDirectory` at `target/test-workdir` and the `publisher.data.dir` property at `target/test-datadir`. They must stay different — if they point at the same place, "legacy file" and "target file" become one path and the `AppPaths` migration tests pass without testing anything.

## Architecture

### Publishing pipeline (the core flow)

`DashboardController.handleStart()` captures the target, category and file on the FX thread, then hands off to **`BatchPublishingService.publishAll(...)`**, which loops the titles and calls **`ArticlePublishingService.publishArticle(...)`** per title. That method is the single shared entry point for turning a title into a published post, reused by the batch loop, the per-row retry button in `PublishLogController`, and (content-generation only, via `generateContent(...)`) the preview dialog.

`ArticlePublishingService` internally:
1. Resolves the effective settings for the target (`PublishingProfileService.resolve`, see below).
2. Optionally translates the title to English (`TranslationService`) if the resolved title/content/slug language requires it.
3. Generates the article HTML (`AIService`, given the resolved AI provider).
4. Fetches a header image (`ImageService`, given the resolved image provider).
5. Publishes through `ProviderRegistry.publisher(target.getPlatform())`, then writes a `PublishLog` via `PublishLogService`.

Before publishing a title, the batch asks a `DuplicateGuard` whether that title has already been published to that target. The fingerprint is the normalised title plus the target id — the same title on two blogs is intentional, on one blog it is a repeat — and it also catches a title listed twice in one file. It reads `publish_logs.json` once per batch, not per article, since `PublishLogService.getAllLogs()` re-reads the file on every call. A failed attempt does not block a retry. The `skip.duplicates` setting turns it off.

### Publish queue survives closing the app

`BatchPublishingService.publishAll` writes a `PublishJob` (`publish_queue.json`, via `PublishQueueService`) before the loop starts, and updates the matching `QueueItem`'s status after every single article — not once at the end, or a crash mid-batch would lose exactly the state this exists to protect. A job has no status field of its own; whether it is resumable is entirely `PublishJob.hasPending()` (any `QueueItem` still `PENDING`), because on app start nothing is "currently running" regardless of what a stale status string might have said — presence of pending work is the only signal that matters. `PublishQueueService.finish(jobId)` deletes the job once nothing is pending, so the file only ever holds interrupted work, never a history (that is `publish_logs.json`'s job).

`BatchPublishingService.resumeJob(job, ...)` and `publishAll(...)` share one internal loop (`runJob`): a `QueueItem` that is not `PENDING` (left over from an earlier partial run) is tallied into the result without re-publishing it or consuming a delay cycle, exactly like a duplicate-skip is. Do not give `BatchPublishingService` a way to depend on JavaFX or on `PublishQueueService` being swappable — it calls the real singleton directly, the same way `ArticlePublishingService` calls `PublishLogService` directly.

`DashboardController.runBatch` takes a `BatchRunner` strategy so `startPublishing`, `retryFailed`, and resuming a queued job (`resumeQueuedJob`) share the same progress bar, stop button, and outcome report instead of three separate execution paths. `PublishTargetResolver.resolve(targetId, targetName)` rebuilds a `PublishTarget` from a `PublishJob`'s stored identity — WordPress resolves fully from `WordPressSiteService`, Blogger resolves with no URL, which only affects the constructed-fallback-link path in `BloggerPublisher` and never the publish call itself. This is a deliberately separate, simpler copy of the same idea in `PublishLogController`'s retry logic, not a shared abstraction — the two screens' URL-caching needs differ enough that unifying them would have meant risking tested, working code for a three-line branch.

### Per-target publishing profiles

A `PublishingProfile` (`publishing_profiles.json`, one entry per target id) overrides the general settings for a single target — a site that should always publish in English while everything else is Arabic, say, without flipping the general settings before every batch and back after. Every field is nullable and `null` means "use the general setting"; `PublishingProfileService.resolve(target)` merges a profile with `ConfigService` into an `EffectiveSettings` record that has no nulls except `defaultCategory`, which has no general-settings equivalent to fall back to. `target` may be `null` (the preview dialog before a blog is chosen) and resolves to the general settings alone.

`AIService` and `ImageService` both take an optional provider-id constructor argument for this — `new AIService(settings.aiProvider())` — which is `null` when nothing overrides it, in which case they fall back to `ConfigService` exactly as before. A profile with every field empty is not written to disk (`PublishingProfile.isEmpty()`); saving one is how the dashboard's profile dialog resets a target back to the general settings.

`DashboardController.selectCategoryCard` is the same code path for a user's click and for auto-selecting a profile's `defaultCategory` once its categories load — do not duplicate the selection logic for the automatic case.

`BatchPublishingService` deliberately knows nothing about JavaFX — it reports progress through a `ProgressListener` and asks a `BooleanSupplier` whether it was cancelled. Keep it that way: it is what stops UI access from a background thread, and what makes partial-failure paths testable through its swappable `ArticlePublisher`. It returns a `BatchPublishResult` (counts, the failures with reasons, how many were never attempted); never discard that result and report success unconditionally, which is the bug it exists to prevent.

### Network calls retry themselves

Every `OkHttpClient` comes from `http.HttpClients`, which installs `RetryInterceptor`; do not build one directly or it silently loses the retry behaviour. Retries happen per request, never per article — republishing an article regenerates its content and can create a second post. The interceptor distinguishes what the server did: `429`/`408`/`425` mean the request was refused before processing so they retry for any method, while `5xx` and read timeouts retry only for idempotent methods, because `POST /wp-json/wp/v2/posts` may have created the post before failing to answer. `BloggerService` uses the Google client, not OkHttp, so it is not covered.

### Providers are registered, not branched on

Adding a content generator, an image provider, or a publish platform means **writing one class and adding one line to `ProviderRegistry.registerBuiltIns()`** — no existing class changes. The `provider` package holds `ContentGenerator`, `ImageProvider` and `Publisher` plus their implementations. `AIService` and `ImageService` are thin façades that look the provider up in `ProviderRegistry` — from an explicit override if given, otherwise from `ConfigService` — and hold no clients of their own.

Each provider builds its API client on first use and rebuilds it when the key changes, so passing provider instances around for connection reuse is unnecessary — only `TranslationService` is still passed through the batch, because it caches an `OkHttpClient` and the discovered Gemini model. An unknown provider id falls back to the first registered one with a warning, but an unregistered `PublishPlatform` throws: publishing to the wrong blog is worse than failing. `resetToBuiltIns()` exists because the registry is static for the whole JVM — any test that registers a provider must call it afterwards.

### Article prompts and models

Every content generator builds its request from `ArticlePrompt.forArticle(title, language)` — one place for the writing rules, target length, optional FAQ section and the user's free-text instructions (all from `ConfigService`). Do not reintroduce per-provider prompt strings. Replies from every provider go through `util.HtmlCleaner` to strip code fences and chatty preambles. Model names are settings (`openai.model`, `gemini.model`, `claude.model`), never literals in a service; an empty `gemini.model` keeps the old auto-discovery, which skips preview/experimental and non-text models. `ArticlePrompt.maxOutputTokens` is deliberately generous: Arabic and "thinking" models both eat tokens, and a too-small cap truncated articles silently. `ClaudeService` treats `stop_reason: max_tokens` as a failure for the same reason.

### Title generator

The dashboard's "Generate titles" button opens `controller.TitleGeneratorDialog` (its own class, deliberately not more code in `DashboardController`). `TitleIdeasService` asks the target's AI provider (via `ContentGenerator.complete`) for a JSON array of titles and also accepts a numbered list when a model ignores the format. Unlike `SeoService` it throws on failure, because the user is waiting on the result. Titles already published to the selected target (`DuplicateGuard`) are shown unticked. The chosen titles are written with `ExcelService.writeTitles` and selected through `DashboardController.useExcelFile`, the same path an uploaded file takes, so preview, scheduling, the queue and publishing need no new branch.

### Internal links

When `internal.links` is on (the default), `InternalLinks.addTo` appends a "Read also" / "اقرأ أيضًا" section with up to 3 earlier articles from the same target. Candidates come from `publish_logs.json` (successful, non-draft, with a URL, same `blogId`, same script as the article — an Arabic title is never linked from an English article). Relevance is shared title keywords after dropping stop words and the Arabic definite article; a leading conjunction "و" adds both forms rather than being stripped, since some words genuinely start with it. No shared keyword means no links — never random ones. The article's own original title is excluded so a retry does not link to its earlier copy. It runs after the quality check and SEO, never throws, and does nothing for a `null` target (the preview before a blog is chosen).

### Social sharing after publishing

After a successful publish, `ArticlePublishingService.shareAfterPublish` hands the article to `SocialSharing.shareAll`, which loops `ProviderRegistry.socialSharers()` (`provider.SocialSharer`: `TelegramSharer` via Bot API `sendPhoto`/`sendMessage`, `FacebookPageSharer` via Graph API `POST /{page-id}/feed`). A sharer is not a `Publisher`: it gets a `SharedPost` (published title, SEO description, public URL, and the header image only if it is a hosted `http(s)` URL — never a `data:` URI), not the article HTML. Drafts and posts without an `http(s)` URL are never shared, because a draft's URL is its editor link.

Sharing must never fail the article or cause a republish: `SocialSharing` never throws (it catches `Exception` per sharer, so one broken network does not stop the others), and the success `PublishLog` is written **before** sharing and then updated through `PublishLogService.updateSharing` (matched on `blogId` + `postId`) with `sharedTo`/`shareError`. Writing it after sharing would leave a crash mid-share with no success log, and resuming the queue would publish the article again.

Where a target shares is per target (`PublishingProfile.telegramChat` / `facebookPageId`, resolved into `EffectiveSettings`; `null` = no sharing, there is no general fallback). Tokens are general settings (`telegram.bot.token`, `facebook.page.token`) in `ConfigService.SECRET_KEYS`, so they are DPAPI-encrypted like API keys. The Facebook token may be a Page token or a long-lived User token: `FacebookPageSharer` first asks `GET /{page-id}?fields=access_token` and uses the configured token as-is when that returns nothing, caching the result per page. The Telegram token is part of the request path and `RetryInterceptor` puts the full URL in its give-up message, so `TelegramSharer` redacts the token from every error before it reaches the log. Only Facebook Pages are possible — Meta closed personal-profile posting in 2018 and removed the groups API in 2024. Adding a network is one `SocialSharer` class, one line in `registerBuiltIns()`, and a destination field on `PublishingProfile`.

### Dashboard dialogs live in their own classes

The full screens (Settings, WordPress sites, Categories, Publish logs, Queue) open as closable tabs in the main window's `workspaceTabs` through `controller.WorkspaceTabs`, not as modal windows: opening one that is already open selects its tab, and the dashboard refreshes what a screen may have changed whenever its tab is left or closed. A screen closes itself with `WorkspaceTabs.close(anyNodeInside)` — **never** `getScene().getWindow().close()`, which inside a tab would close the whole app. `WorkspaceTabs.close` closes the tab when there is one and the window otherwise, and fires the tab's `onClosed` by hand because removing a tab in code does not.

`DashboardController` hosts the FXML screen and the batch flow only. The preview dialog (`ArticlePreviewDialog`), the per-target settings dialog (`PublishingProfileDialog`), the title generator (`TitleGeneratorDialog`) and the scheduling controls with their executor and countdown (`PublishScheduleControls`) are package-private classes in `controller/`, built in code rather than FXML and handed what they need (target, callbacks, `friendlyErrorMessage` as a `Function`). Add new dialogs the same way instead of growing the controller again.

### First-run setup wizard

`MainApp` starts with `controller.SetupWizard` instead of the login screen when `FirstRun.needsSetup()` — no `setup.completed` flag, no configured content generator and no saved WordPress site, so someone upgrading with existing settings never sees it. `controller.Screens` owns the three full-window screens (wizard, login, dashboard); use it for any transition between them rather than building a scene by hand. It gives the new root the old scene's size before swapping, because a scene set on a showing stage takes its root's preferred size, not the window's, and the login card (400×560) otherwise sits in a corner of a 1000×720 window.

Every key is checked before it is saved, through `KeyedProvider.checkKey` → `service.KeyCheck`, a free request (model list) per provider. The result is a value, not an exception: `REJECTED` (the provider refused the key — never saved), `VALID`, or inconclusive (`UNREACHABLE`/`FAILED`), where a second press of Next saves it anyway so a provider outage cannot block setup. The check says nothing about remaining credit. The Gemini check sends the key in the `x-goog-api-key` header, not `?key=`, since `RetryInterceptor` logs the full URL. The wizard's provider list comes from `ProviderRegistry`: a provider appears there by returning a non-null `apiKeySetting()`, with no wizard change.

The publishing step defaults to WordPress because it needs no Google project; `LoginController` has a matching "Continue without Google" button, and the dashboard already lists saved WordPress sites when Blogger is not signed in. For Blogger the wizard takes the downloaded OAuth file through `GoogleCredentialsFile`, which rejects a "Web application" client by name (the usual mistake) and copies the file to the data directory. Picking a file that differs from the saved one clears the stored Google session, since those tokens were issued to another OAuth client.

Google sign-in waits for the browser with no timeout (`LocalServerReceiver`), so three things keep an abandoned sign-in from freezing anything: the wizard does not disable navigation while it runs; `BloggerService.authorizeInBrowser` is deliberately not `synchronized` (it used to hold the service monitor for the whole wait, blocking `getMyBlogs` behind it forever); and `BloggerService.cancelLogin()` stops the receiver, called when the wizard finishes and by "Continue without Google". A new sign-in also cancels a pending one, which still holds port 8888. Independently of all that, `DashboardController.loadBlogs` lists the saved WordPress sites before it asks Blogger for anything — never make local targets wait on a network call again.

### Quality check before publishing

`ArticlePublishingService.generateCheckedArticle` runs every generated article through `ArticleQualityCheck` (empty reply, an apology in the first 200 characters, Markdown instead of HTML, a reply that does not end on a closing tag, no `<h2>`, fewer than half the words the chosen length asks for). A rejected article is regenerated **once** — safe because nothing has been published yet — and a second rejection throws an `IOException` with the reason, so it lands in the publish log as an ordinary retryable failure. The rules are deliberately lenient: rejecting a good article costs more than letting a mediocre one through. `quality.check` turns it off.

### SEO metadata

After the article is generated, `AIService.generateSeo` asks the same provider (through `ContentGenerator.complete`, a default method that throws when a provider does not support it) for a JSON object with a meta description, tags and image alt text. `SeoService` never throws: a failed call, a non-JSON reply or the `seo.ai` setting being off all fall back to data taken from the article itself (first real paragraph, title as alt text, no tags). The result travels in `ArticleContent.seo()`, never null. WordPress gets all three (`excerpt`, tag ids resolved/created like categories and cached per site, `alt_text` set on the uploaded featured image in a follow-up request); Blogger only gets the alt text inside `contentHtml()`, because its API has no search-description field and its labels double as the app's categories.

### Header images

`OpenAIImageProvider` (id still `"dalle"`, the value stored in existing configs and profiles) calls the model in `openai.image.model` (`gpt-image-1` by default; `dall-e-*` still works) at the quality in `openai.image.quality` (`low`/`medium`/`high`, default `medium`; the main cost driver, and ignored for `dall-e-*`, which rejects those values) and returns the image as a `data:` URI, never a link — OpenAI image links expire after about an hour, which used to leave Blogger posts with broken images. `ImageProvider.returnsHostedUrl()` says which kind a provider returns. WordPress uploads either kind (`WordPressService.uploadMedia` decodes a data URI instead of downloading). Blogger cannot host an uploaded image, so `ImageService.getImageForArticle(title, target)` swaps in Unsplash for a Blogger target, or returns `null` (no image, logged) without an Unsplash key. A data URI is never embedded into a WordPress post body, even when the upload fails.

Unsplash searches return up to 30 results and the first one not already used on the same target is taken, so similar titles on one blog stop sharing one image. Used images come from `PublishLog.imageUrl` (`UsedImages.forTarget`), compared without the query string because Unsplash changes `ixid` on every request; a `data:` image is never logged. `UsedImages.select` also reserves each chosen image in memory and picks under a per-target lock, because the log is only written after publishing: two retries of the same blog running on separate threads would otherwise read the same log and pick the same photo. When every result is used the top one is reused rather than publishing without an image.

### Windows installer

`packaging/build-installer.ps1` runs `jpackage` on the shaded jar (non-modular: several dependencies are automatic modules, so a modular jlink image is not possible) with `Launcher` as the main class and a trimmed runtime. The `$Modules` list there is `jdeps --print-module-deps` output **plus** modules only loaded reflectively (`jdk.localedata` for Arabic, `jdk.charsets`, `jdk.crypto.ec`, `jdk.zipfs`, `java.logging`, `jdk.accessibility`); a new dependency may need a module added there, which surfaces only when the packaged app runs. `--win-upgrade-uuid` is fixed and must never change, or installers stop upgrading each other. The installer version comes from `pom.xml` and must increase for an upgrade to take. `credentials.json` from the project root is bundled next to the jar unless `-NoCredentials` is passed.

### App icon

Window icons come from `util.AppIcons`, which hands the stage every size (16–256) from `images/app-icon-<size>.png`; the vector source and regeneration steps are in `docs/branding`.

### Draft mode

`post.status` (`publish`/`draft`, overridable per target through `PublishingProfile.postStatus`) is resolved into `EffectiveSettings.draft()` and passed to `Publisher.publish(..., draft)`. A draft has no public URL, so publishers return the editor link instead (Blogger's post editor, WordPress's `wp-admin/post.php?post=ID&action=edit`), and the `PublishLog` is a success with `draft = true` — it still counts as published for `DuplicateGuard`, since the post exists on the site.

### Multi-platform publish targets

`PublishTarget` is the unifying model (`id`, `name`, `url`, `PublishPlatform` enum, plus WordPress-only `wpUsername`/`wpApplicationPassword`) that lets the dashboard's blog dropdown mix Blogger blogs (fetched live via `BloggerService.getMyBlogs()`) and saved WordPress sites (`WordPressSiteService`, persisted locally) in one list. One `Publisher` implementation per platform hides how each returns the published post:
- **`BloggerPublisher`**: `BloggerService.publishPost(...)`, then resolves the real post URL via `getPostUrl`, falling back to a constructed URL if that call fails.
- **`WordPressPublisher`**: `WordPressService.publishPost(...)` via the WP REST API with Basic Auth (Application Password); the post URL comes straight back in the create-post response. `WordPressService` refuses to send credentials to a non-HTTPS URL (loopback excepted) — the rule lives in `WordPressService.isSecureUrl` and is enforced both there and in `WordPressSitesController`.

WordPress posts now carry a category: the dashboard lists the site's categories alongside Blogger's labels, and `WordPressPublisher` resolves the chosen name to a term id, creating the term if it is missing and caching the mapping per site so a batch does not re-resolve it per article. Failing to resolve it logs a warning and publishes uncategorised rather than failing the article. Selecting one stays optional for WordPress (unlike Blogger) because WP publishes to Uncategorised happily, and requiring it would block a site with no categories yet.

`ArticleContent` keeps the body and the header image apart on purpose: `bodyHtml()` carries no image, and `contentHtml()` composes the two for a platform with no separate featured image. `WordPressPublisher` uploads the image to the media library and sets `featured_media`, publishing `bodyHtml()` so the image does not appear twice; if the upload fails it falls back to `contentHtml()` and the article still goes out with its image. Blogger and the preview dialog use `contentHtml()`.

The internal blog-grouping feature in the Categories screen (`CategoryService`/`BlogCategory`, used only to filter the dashboard's blog dropdown) works on `PublishTarget` ids, so Blogger blogs and WordPress sites are both assignable. Saved WordPress sites are listed immediately since they are local, and Blogger blogs are appended when the network call returns — a Blogger login failure used to leave the list empty and block assigning anything at all.

When resolving a `PublishTarget` from a stored `PublishLog` (blogId/blogName strings only — see retry logic in `PublishLogController`), check `WordPressSiteService.getSiteById(blogId)` first before falling back to treating it as a Blogger blog ID.

### Services are singletons with local JSON/properties persistence

`ConfigService`, `CategoryService`, `PublishLogService`, `WordPressSiteService` all follow the same shape: private constructor + static `INSTANCE`, load-on-construct from a relative-path file, atomic write (write to `.tmp` then `Files.move` with `ATOMIC_MOVE`) on save, and corrupted-JSON files get backed up with a timestamp suffix rather than crashing the app. Follow this pattern for any new locally-persisted service rather than introducing a different persistence approach.

`BloggerService` is also a singleton but manages OAuth state (`tokens/` directory) and wraps the Google Blogger API client instead of local JSON.

### JavaFX/FXML conventions

- Every screen is an FXML file under `src/main/resources/com/hamzatech/publisher/view/` paired 1:1 with a controller in `controller/`.
- The app uses **AtlantaFX** (`PrimerLight`/`PrimerDark`, toggled via `ThemeManager`, applied through `Application.setUserAgentStylesheet`) for theming and **Ikonli** (Feather icon pack, `fth-*` literals) for icons. New UI should use AtlantaFX style classes (`accent`, `success`, `danger`, `warning`, `title-1`..`title-4`, `text-muted`, `surface-card`, `app-toolbar`, `content-area` — the last few are custom classes defined in `view/styles.css`) instead of hardcoded inline colors. Brand hex values live in `util.AppColors` as the single source of truth when a raw `Color.web(...)` is unavoidable (e.g. `TableCell` styling).
- Section headings get their icon as a `<graphic><FontIcon .../></graphic>` on the `Label` (Feather, `iconSize="18"`, `styleClass="accent"`, `graphicTextGap="8"`), never as an emoji inside the resource-bundle text — emoji render inconsistently and were removed from both properties files.
- The main window is `StageStyle.EXTENDED`: its minimize/maximize/close buttons are drawn by JavaFX (`HeaderButtonOverlay`), which picks light or dark glyphs from **`Scene.getFill()`** — not from the AtlantaFX theme and not from `Scene.Preferences.colorScheme`. `AppWindow.createScene` therefore updates the fill on every theme change, but one pulse later (`Platform.runLater`): changing the fill in the same pulse as `Application.setUserAgentStylesheet` left the buttons unstyled, so they vanished after the first toggle. The window background itself comes from CSS (`.app-shell`), not from the fill.
- Every window other than the main one uses the native Windows title bar, which `util.DarkTitleBar` darkens in dark mode with `DwmSetWindowAttribute` (via JNA's `Function`, not a proxy `Library` interface) whenever a window is shown and whenever the theme changes. `Scene.getPreferences().setColorScheme(...)` was tried first and does **not** change the title bar on Windows 10, so do not replace this with it. It skips `EXTENDED` windows (matched by title, since JavaFX exposes no HWND): forcing a frame change on the main window shifted and recolored its header buttons.
- `module-info.java` is real JPMS, not decorative — every new dependency that's referenced from `MainApp`'s module needs a `requires` line, and any new package a `FXMLLoader` needs to reflectively construct into needs `opens ... to javafx.fxml` (the existing wildcard `opens` on `controller` and `model` already covers new classes added to those packages).
- Verifying a new/changed FXML screen actually loads (catches missing `fx:id` bindings, bad Ikonli icon literals, etc.) can be done by temporarily pointing the `javafx-maven-plugin`'s `mainClass` in `pom.xml` at a throwaway `Application` that loads each FXML with `FXMLLoader` and prints success/failure — faster than manually logging in and clicking through every screen, and doesn't require real Blogger/WordPress credentials.

### i18n / RTL

`LocaleManager` (singleton, `Preferences`-backed) holds the current `Locale` (`ar` or `en`) and exposes `getResourceBundle()` (`i18n/messages_ar.properties` / `messages_en.properties`) and `applyOrientation(Scene)` for RTL/LTR. Every user-facing string in FXML uses `%key` resource-bundle syntax; add new strings to *both* properties files. Arabic property values in these files are written as literal UTF-8 text (not `\uXXXX` escapes) when edited directly — the existing `\uXXXX`-escaped lines were generated by an older `native2ascii`-style tool and both forms coexist and load fine since Java 9+ reads `.properties` as UTF-8 by default.

### Misc

- `GeminiKeyTester` is a standalone CLI utility with its own `main()` for smoke-testing a Gemini API key outside the app — not part of the app's runtime flow.
- `Launcher` is the shaded-jar entry point (see the `maven-shade-plugin` config in `pom.xml`); `MainApp` is the entry point used by `mvn javafx:run` and by IDE run configs.
