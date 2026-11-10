<#
.SYNOPSIS
    Builds a Windows installer for Hamza Publisher with jpackage.

.DESCRIPTION
    Produces target\installer\Hamza Publisher-<version>.exe: a per-user installer that bundles its
    own trimmed Java 25 runtime, so the machine it is installed on needs no JDK. It adds a Start
    menu entry and a desktop shortcut, and a fixed upgrade UUID makes a newer installer replace an
    older install instead of installing side by side.

    -Type app-image builds a portable folder (zipped) instead, which needs no WiX.

.PARAMETER Type
    exe (default), msi or app-image.

.PARAMETER NoCredentials
    Do not bundle credentials.json even if it exists in the project root. Without it, users
    must place their own credentials.json in %LOCALAPPDATA%\HamzaPublisher before signing in to
    Blogger.

.PARAMETER SkipBuild
    Reuse target\publisher-<version>.jar instead of running "mvnw package".

.EXAMPLE
    .\packaging\build-installer.ps1
.EXAMPLE
    .\packaging\build-installer.ps1 -Type app-image -NoCredentials
#>
param(
    [ValidateSet('exe', 'msi', 'app-image')]
    [string]$Type = 'exe',
    [switch]$NoCredentials,
    [switch]$SkipBuild,
    [string]$WixPath
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

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

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $jdks = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path $jdks) {
        $candidates += Get-ChildItem $jdks -Directory | Where-Object { $_.Name -match '25' } |
            Sort-Object Name -Descending | ForEach-Object { $_.FullName }
    }
    foreach ($jdkHome in $candidates) {
        $jpackage = Join-Path $jdkHome 'bin\jpackage.exe'
        $release = Join-Path $jdkHome 'release'
        if ((Test-Path $jpackage) -and (Test-Path $release)) {
            $line = Select-String -Path $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
            if ($line -and [int]$line.Matches[0].Groups[1].Value -ge 25) { return $jdkHome }
        }
    }
    throw 'JDK 25 or later not found. Set JAVA_HOME to a JDK 25 installation.'
}

function Find-Wix {
    if ($WixPath) {
        if (Test-Path (Join-Path $WixPath 'candle.exe')) { return $WixPath }
        throw "candle.exe not found in -WixPath '$WixPath'."
    }
    $onPath = Get-Command candle.exe -ErrorAction SilentlyContinue
    if ($onPath) { return (Split-Path -Parent $onPath.Source) }
    $portable = Join-Path $env:USERPROFILE '.tools\wix314'
    if (Test-Path (Join-Path $portable 'candle.exe')) { return $portable }
    return $null
}

$jdk = Find-Jdk
$env:JAVA_HOME = $jdk
Write-Host "JDK:  $jdk"

if ($Type -ne 'app-image') {
    $wix = Find-Wix
    if (-not $wix) {
        throw ("WiX Toolset 3 is required for -Type $Type. Install it (winget install WiXToolset.WiXToolset), " +
               "unzip wix314-binaries.zip into $env:USERPROFILE\.tools\wix314, pass -WixPath, " +
               "or use -Type app-image.")
    }
    $env:PATH = "$wix;$env:PATH"
    Write-Host "WiX:  $wix"
}

# jpackage accepts only 1-3 numeric components, so 1.0-SNAPSHOT becomes 1.0.
[xml]$pom = Get-Content (Join-Path $root 'pom.xml')
$pomVersion = $pom.project.version
$appVersion = ($pomVersion -replace '-.*$', '')
if ($appVersion -notmatch '^\d+(\.\d+){0,2}$') {
    throw "Cannot turn the pom version '$pomVersion' into an installer version."
}
Write-Host "Version: $appVersion (pom $pomVersion)"

$jarName = "publisher-$pomVersion.jar"
$jar = Join-Path $root "target\$jarName"
if (-not $SkipBuild) {
    # A previous installer can come out read-only, and "mvn clean" cannot delete read-only files;
    # Remove-Item -Force can, so clear the old output before Maven tries.
    $oldOutput = Join-Path $root 'target\installer'
    if (Test-Path $oldOutput) { Remove-Item $oldOutput -Recurse -Force }
    Write-Host 'Building the runnable jar...'
    & (Join-Path $root 'mvnw.cmd') -q -DskipTests clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
}
if (-not (Test-Path $jar)) { throw "Jar not found: $jar" }

# jpackage copies everything in --input into the app folder, so stage only what belongs there.
$staging = Join-Path $root 'target\installer-input'
$dest = Join-Path $root 'target\installer'
foreach ($dir in @($staging, $dest)) {
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    New-Item -ItemType Directory -Path $dir | Out-Null
}
Copy-Item $jar (Join-Path $staging 'publisher.jar')

$credentials = Join-Path $root 'credentials.json'
if ((Test-Path $credentials) -and -not $NoCredentials) {
    # BloggerService looks next to the jar first, so a bundled file just works.
    Copy-Item $credentials $staging
    Write-Host 'Bundling credentials.json (your Google OAuth client). Use -NoCredentials to leave it out.' -ForegroundColor Yellow
} else {
    Write-Host 'Not bundling credentials.json: users place their own in %LOCALAPPDATA%\HamzaPublisher.'
}

$jpackageArgs = @(
    '--type', $Type,
    '--name', $AppName,
    '--app-version', $appVersion,
    '--vendor', $Vendor,
    '--description', 'Generate and publish articles to Blogger and WordPress with AI',
    '--icon', (Join-Path $root 'tool.ico'),
    '--input', $staging,
    '--main-jar', 'publisher.jar',
    '--main-class', $MainClass,
    '--dest', $dest,
    '--add-modules', $Modules,
    '--jlink-options', '--strip-debug --no-header-files --no-man-pages --strip-native-commands',
    # JNA (secret encryption) and JavaFX call native code; without this Java 25 prints warnings.
    '--java-options', '--enable-native-access=ALL-UNNAMED'
)
if ($Type -ne 'app-image') {
    $jpackageArgs += @(
        '--win-per-user-install',
        '--win-dir-chooser',
        '--win-menu', '--win-menu-group', $Vendor,
        '--win-shortcut', '--win-shortcut-prompt',
        '--win-upgrade-uuid', $UpgradeUuid
    )
}

Write-Host "Running jpackage ($Type)..."
& (Join-Path $jdk 'bin\jpackage.exe') @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }

if ($Type -eq 'app-image') {
    $zip = Join-Path $dest "$AppName-$appVersion-portable.zip"
    Compress-Archive -Path (Join-Path $dest $AppName) -DestinationPath $zip
    Write-Host "Portable build: $zip" -ForegroundColor Green
} else {
    Get-ChildItem $dest -Filter "*.$Type" | ForEach-Object {
        Write-Host ("Installer: {0} ({1:N0} MB)" -f $_.FullName, ($_.Length / 1MB)) -ForegroundColor Green
    }
}
