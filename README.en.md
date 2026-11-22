# Hamza Publisher

[![CI](https://github.com/MohamedSalah-13/publisher/actions/workflows/ci.yml/badge.svg)](https://github.com/MohamedSalah-13/publisher/actions/workflows/ci.yml) ![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white) ![JavaFX](https://img.shields.io/badge/JavaFX-25-1F6FB2) ![Blogger](https://img.shields.io/badge/Blogger-API-FF5722?logo=blogger&logoColor=white) ![WordPress](https://img.shields.io/badge/WordPress-REST-21759B?logo=wordpress&logoColor=white) ![License](https://img.shields.io/badge/License-MIT-yellow)

[العربية](README.md) · **English**

A desktop app that auto-publishes articles to **Blogger** blogs and **WordPress** sites from a list of titles in an Excel file. For each title it writes the article with AI, fetches a header image, adds SEO metadata and internal links, publishes, then shares the link to Telegram or Facebook.

It talks to the providers directly with your own API keys: no monthly subscription, no middleman server, and all your data stays on your machine.

![Dashboard](docs/portfolio/en/03-dashboard.png)

## Features

- **Two platforms in one list:** Blogger blogs (Google OAuth) and WordPress sites (Application Passwords).
- **Three content providers:** OpenAI, Gemini and Claude; the model name is a setting.
- **Header images:** from Unsplash or generated with OpenAI image models, without reusing the same image on one blog.
- **Quality check before publishing:** an empty, truncated or Markdown reply is regenerated once, then logged as a retryable failure.
- **SEO:** meta description, tags and image alt text, plus a "Read also" section linking to related earlier articles on the same blog.
- **Duplicate guard:** a title already published to the same blog is skipped.
- **A queue that survives closing the app:** an interrupted batch resumes where it stopped without republishing anything.
- **Per-blog profiles:** a different language, provider, post status and default category per target.
- **Draft mode:** publish as drafts and review before readers see them.
- **Title generator**, article preview, and scheduling a batch for later.
- **Automatic sharing** to a Telegram channel or a Facebook Page after publishing.
- **Arabic (RTL) and English UI**, light and dark themes.
- **Secrets encrypted at rest** with Windows DPAPI.

## Install

Choose the installer for your system and processor from [Releases](https://github.com/MohamedSalah-13/publisher/releases). Each package bundles Java.

- **Windows 10/11 (x64)** — run the `.exe` to install for the current user.
- **Ubuntu 24.04 and compatible Debian-based distributions (x64 or ARM64)** — install the matching `.deb` with `sudo apt install ./<installer>.deb`.
- **macOS (Intel or Apple Silicon)** — open the matching `.dmg` and drag Hamza Publisher to Applications. The builds don't include Apple Developer ID signing or notarization.

Installed copies check GitHub Releases automatically. The dashboard also has **Check for updates**. On Linux and macOS, the app downloads and opens the installer for you to finish. The Windows portable ZIP is updated manually.

## Setup

On first launch a **setup wizard** walks you through three steps: a content provider key, an image key, and where to publish. Each key is checked with its provider before it is saved, and every step can be skipped. Publishing to WordPress needs no Google account at all.

Everything in the wizard can be changed later in the **Settings** screen, and the wizard can be reopened from the sign-in screen.

| What you need | When | Where to get it |
|---|---|---|
| A key for at least one content provider | Always | [OpenAI](https://platform.openai.com/api-keys), [Gemini](https://aistudio.google.com/app/apikey) or [Claude](https://console.anthropic.com/settings/keys) |
| `credentials.json` | Publishing to Blogger | Google Cloud Console (steps below) |
| Application Password | Publishing to WordPress | WordPress admin → Users → Profile |
| Unsplash key | Images (required for Blogger images) | [Unsplash Developers](https://unsplash.com/oauth/applications) |
| Telegram bot token / Facebook Page token | Sharing, optional | See the [Arabic README](README.md) |

### Blogger: `credentials.json`

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create a project.
2. Under **APIs & Services → Library**, enable **Blogger API v3**.
3. Configure the **OAuth consent screen** (External) and add your account under **Test users**.
4. Under **Credentials → Create Credentials → OAuth client ID**, choose **Desktop app**.
5. Download the JSON, name it `credentials.json`, and put it in `%LOCALAPPDATA%\HamzaPublisher` (or the project root when running from source).

While the Google app is in Testing mode, the sign-in expires every 7 days and you have to sign in again.

### WordPress

In the **WordPress sites** screen, add the site URL, username and an Application Password. The URL must be `https`; the app refuses to send credentials over an unencrypted connection.

## Usage

1. **Prepare titles:** an `.xlsx` file with titles in the first column; the first row is a header and is skipped. Or use **Generate titles** to have the AI suggest them.
2. **Pick the target** (a Blogger blog or a WordPress site) and a category. The category is required for Blogger and optional for WordPress.
3. **Preview** an article if you like, then press **Start**.
4. **Check the results** in the publish log: each article's link, and the reason for any failure with a retry button.

> **Tip:** search engines penalise mass-generated content published without review. Use draft mode and review articles before they go live.

## Run from source

Requires **JDK 25** or later (the build fails on anything older). Maven is included through `mvnw`.

```bash
git clone https://github.com/MohamedSalah-13/publisher.git
cd publisher
./mvnw javafx:run
```

| Command | Purpose |
|---|---|
| `./mvnw test` | Run the tests |
| `./mvnw clean package` | Build the runnable jar in `target/` |
| `.\packaging\build-installer.ps1` | Build the Windows installer in `target\installer\` |
| `pwsh ./packaging/build-installer.ps1` | Build a native `.deb` on Linux or `.dmg` on macOS in `target/installer/` |

Windows packaging needs [WiX Toolset 3](https://github.com/wixtoolset/wix3/releases), except for `-Type app-image` (portable ZIP). Linux packaging needs `fakeroot`, `binutils` and `dpkg-deb`. A `credentials.json` in the project root is bundled into the installer unless you pass `-NoCredentials`. The script needs a clone of [desktop-updater](https://github.com/MohamedSalah-13/desktop-updater) beside this repository (or `DESKTOP_UPDATER_HOME`): the packaging steps the programs share are its `tools/Packaging.psm1`.

Run the **CI** workflow with **build_installers** enabled to build downloadable installers without publishing a release. A version tag on a commit containing the packaging workflow builds and publishes every platform. Set the version in `pom.xml` to match the new tag first.

## Where data lives

Settings, logs, the queue and the Google session live in `%LOCALAPPDATA%\HamzaPublisher` on Windows and `$XDG_DATA_HOME/hamza-publisher` or `~/.local/share/hamza-publisher` on Linux and macOS. Windows encrypts API keys and WordPress passwords with DPAPI tied to the current account. Linux and macOS store those values as plain text.

## Contributing

Architecture and conventions are documented in [CLAUDE.md](CLAUDE.md). Adding a content provider, an image provider or a publish platform is one class plus one line in `ProviderRegistry.registerBuiltIns()`. Open an issue or send a pull request.

## License

[MIT](LICENSE)
