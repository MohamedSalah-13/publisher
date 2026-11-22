<#
.SYNOPSIS
    Builds a native installer for Hamza Publisher with jpackage.

.DESCRIPTION
    Produces target\installer\Hamza Publisher-<version>.exe: a per-user installer that bundles its
    own trimmed Java 25 runtime, so the machine it is installed on needs no JDK. It adds a Start
    menu entry and a desktop shortcut, and a fixed upgrade UUID makes a newer installer replace an
    older install instead of installing side by side.

    On Windows, -Type app-image builds a portable zip instead, which needs no WiX.
    On Linux, -Type deb builds a Debian package. On macOS, -Type dmg builds a disk image.
    Run on the target OS with a JDK for the target architecture.

    The steps every program's packaging script repeats (finding the JDK and WiX, Maven, the version,
    the installer's options, the published name) come from desktop-updater's tools/Packaging.psm1,
    found in a clone of desktop-updater beside this repository or where DESKTOP_UPDATER_HOME points.
    What is Hamza Publisher's own stays here.

.PARAMETER Type
    exe (default on Windows), msi, app-image, deb (Linux) or dmg (macOS).

.PARAMETER NoCredentials
    Do not bundle credentials.json even if it exists in the project root. Without it, users
    can import their own credentials.json in the setup wizard before signing in to Blogger.

.PARAMETER SkipBuild
    Reuse target\publisher-<version>.jar instead of running "mvnw package".

.EXAMPLE
    .\packaging\build-installer.ps1
.EXAMPLE
    .\packaging\build-installer.ps1 -Type app-image -NoCredentials
#>
param(
    [ValidateSet('exe', 'msi', 'app-image', 'deb', 'dmg')]
    [string]$Type,
    [switch]$NoCredentials,
    [switch]$SkipBuild,
    [string]$WixPath
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# desktop-updater is looked for beside the repository, wherever in it this script sits.
$updaterHome = $env:DESKTOP_UPDATER_HOME
if (-not $updaterHome) {
    $repository = & git -C $root rev-parse --show-toplevel 2>$null
    if (-not $repository) { $repository = $root }
    $updaterHome = Join-Path (Split-Path -Parent $repository) 'desktop-updater'
}
$packaging = Join-Path $updaterHome 'tools/Packaging.psm1'
if (-not (Test-Path -LiteralPath $packaging)) {
    throw "desktop-updater was not found at $updaterHome. Clone https://github.com/MohamedSalah-13/desktop-updater beside this repository, or set DESKTOP_UPDATER_HOME."
}
Import-Module $packaging -Force

$platform = Get-PackagingPlatform
$windows = $platform -eq 'windows'
$supportedTypes = switch ($platform) { 'linux' { 'deb' } 'macos' { 'dmg' } default { 'exe', 'msi', 'app-image' } }
if (-not $Type) { $Type = @($supportedTypes)[0] }
if ($Type -notin $supportedTypes) { throw "Package type '$Type' is unavailable on this OS." }

$AppName = 'Hamza Publisher'
$Vendor = 'Hamza Tech'
$MainClass = 'com.hamzatech.publisher.Launcher'
# Never change this: it is how Windows recognises a newer installer as an upgrade of this app.
$UpgradeUuid = '062007df-78e4-4795-bab3-680bbe2ff3bf'

# Java modules the app needs. The first line is what "jdeps --print-module-deps" reports for the
# shaded jar; the rest are loaded reflectively, which jdeps cannot see: Arabic locale data,
# extra charsets, TLS elliptic curves, zip file system, logging and accessibility.
$Modules = @(
    'java.base', 'java.compiler', 'java.desktop', 'java.management', 'java.naming', 'java.net.http',
    'java.prefs', 'java.rmi', 'java.scripting', 'java.security.jgss', 'java.sql', 'java.xml.crypto',
    'jdk.httpserver', 'jdk.jfr', 'jdk.unsupported', 'jdk.xml.dom',
    'java.logging', 'jdk.localedata', 'jdk.charsets', 'jdk.crypto.ec', 'jdk.zipfs', 'jdk.accessibility'
) -join ','

# JDK 25 or later from JAVA_HOME, PATH or ~/.jdks. Maven runs on the same JDK, since the build needs 25.
$jdk = Find-JPackage -MinimumVersion 25
$env:JAVA_HOME = $jdk.JdkHome
Write-Host "JDK:  $($jdk.JdkHome)"

if ($windows -and $Type -ne 'app-image') {
    if ($WixPath) {
        if (-not (Test-Path (Join-Path $WixPath 'candle.exe'))) { throw "candle.exe not found in -WixPath '$WixPath'." }
        $env:PATH = "$WixPath;$env:PATH"
        $wix = $WixPath
    } else {
        try {
            $wix = Find-WiX
        } catch {
            throw ("WiX Toolset 3 is required for -Type $Type. Install it (winget install WiXToolset.WiXToolset), " +
                   "unzip wix314-binaries.zip into $env:USERPROFILE\.tools\wix314, pass -WixPath, " +
                   "or use -Type app-image.")
        }
    }
    Write-Host "WiX:  $wix"
}

# jpackage accepts only 1-3 numeric components, so 1.0-SNAPSHOT becomes 1.0.
$pomVersion = (Get-PomVersion -Pom (Join-Path $root 'pom.xml') -Platform $platform)
$appVersion = $pomVersion.Version
Write-Host "Version: $appVersion (pom $($pomVersion.Raw))"

$jarName = "publisher-$($pomVersion.Raw).jar"
$jar = Join-Path $root "target/$jarName"
if (-not $SkipBuild) {
    # Clears the read-only installer of an earlier run first, which "mvn clean" cannot delete on JDK 25.
    Write-Host 'Building the runnable jar...'
    Invoke-MavenBuild -Pom (Join-Path $root 'pom.xml') -Arguments @('-q', '-DskipTests', 'clean', 'package')
}
if (-not (Test-Path $jar)) { throw "Jar not found: $jar" }

# jpackage copies everything in --input into the app folder, so stage only what belongs there.
$staging = Join-Path $root 'target/installer-input'
$dest = Join-Path $root 'target/installer'
foreach ($dir in @($staging, $dest)) {
    if (Test-Path $dir) {
        [void](Clear-ReadOnly -Path $dir)
        Remove-Item $dir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $dir | Out-Null
}
Copy-Item $jar (Join-Path $staging 'publisher.jar')

$credentials = Join-Path $root 'credentials.json'
if ((Test-Path $credentials) -and -not $NoCredentials) {
    # BloggerService looks next to the jar first, so a bundled file just works.
    Copy-Item $credentials $staging
    Write-Host 'Bundling credentials.json (your Google OAuth client). Use -NoCredentials to leave it out.' -ForegroundColor Yellow
} else {
    Write-Host 'Not bundling credentials.json: users can import their own in the setup wizard.'
}

$jpackageArgs = @(
    '--type', $Type,
    '--name', $AppName,
    '--app-version', $appVersion,
    '--vendor', $Vendor,
    '--description', 'Generate and publish articles to Blogger and WordPress with AI',
    '--input', $staging,
    '--main-jar', 'publisher.jar',
    '--main-class', $MainClass,
    '--dest', $dest,
    '--add-modules', $Modules,
    '--jlink-options', '--strip-debug --no-header-files --no-man-pages --strip-native-commands',
    # JNA (secret encryption) and JavaFX call native code; without this Java 25 prints warnings.
    '--java-options', '--enable-native-access=ALL-UNNAMED'
)
if ($windows) {
    $jpackageArgs += @('--icon', (Join-Path $root 'tool.ico'))
}
if ($Type -eq 'app-image') {
    $jpackageArgs += @('--java-options', '-Dpublisher.portable=true')
} elseif ($windows) {
    $jpackageArgs += @(Get-WinInstallerArgument -PerUser -DirChooser -Menu -MenuGroup $Vendor -Shortcut -UpgradeUuid $UpgradeUuid)
    $jpackageArgs += '--win-shortcut-prompt'
} elseif ($platform -eq 'linux') {
    $jpackageArgs += @(
        '--icon', (Join-Path $root 'docs/branding/app-icon-512.png'),
        '--linux-package-name', 'hamza-publisher',
        '--linux-shortcut',
        '--linux-package-deps', 'libgtk-3-0 | libgtk-3-0t64, libasound2 | libasound2t64'
    )
} else {
    # Without it the app and the disk image show Java's default icon (tool.icns: docs/branding/IconRender.java).
    $jpackageArgs += @(
        '--icon', (Join-Path $root 'tool.icns'),
        '--mac-package-identifier', 'com.hamzatech.publisher'
    )
}

if (-not $windows) {
    # The JDK's architecture, not the machine's: an x64 JDK under Rosetta builds an x64 package.
    $jdkArch = Select-String -LiteralPath (Join-Path $jdk.JdkHome 'release') -Pattern '^OS_ARCH="([^"]+)"$'
    $arch = switch ($jdkArch.Matches[0].Groups[1].Value) {
        { $_ -in 'x86_64', 'amd64' } { 'x64' }
        { $_ -in 'aarch64', 'arm64' } { 'arm64' }
        default { throw "Unsupported JDK architecture: $_" }
    }
}

Write-Host "Running jpackage ($Type)..."
try {
    Invoke-Native -FilePath $jdk.Path -ArgumentList $jpackageArgs
} catch {
    throw 'jpackage failed.'
}

if ($Type -eq 'app-image') {
    $zip = Join-Path $dest "$AppName-$appVersion-portable.zip"
    Compress-Archive -Path (Join-Path $dest $AppName) -DestinationPath $zip
    Write-Host "Portable build: $zip" -ForegroundColor Green
} elseif ($windows) {
    Get-ChildItem $dest -Filter "*.$Type" | ForEach-Object {
        Write-Host ("Installer: {0} ({1:N0} MB)" -f $_.FullName, ($_.Length / 1MB)) -ForegroundColor Green
    }
} else {
    $package = Rename-PlatformPackage -Directory $dest -Type $Type -Name 'hamza-publisher' -Version $appVersion -Platform $platform -Arch $arch
    Write-Host "Installer: $($package.FullName)" -ForegroundColor Green
}
