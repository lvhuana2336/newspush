param(
    [switch]$SkipDownloads,
    [switch]$CheckOnly,
    [switch]$Build
)

# Native commands may write warnings to stderr (e.g. sdkmanager); do not treat that as fatal,
# check exit codes explicitly instead.
$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"

# Clear the sandbox proxy so downloads can reach the mirrors directly
$env:HTTP_PROXY = ""
$env:HTTPS_PROXY = ""
$env:ALL_PROXY = ""
$env:GIT_HTTP_PROXY = ""
$env:GIT_HTTPS_PROXY = ""
$env:NO_PROXY = "*"

$Root = "D:\AndroidDev"
$Downloads = Join-Path $Root "downloads"
$Logs = Join-Path $Root "logs"
$Sdk = Join-Path $Root "android-sdk"
$JdkHome = Join-Path $Root "jdk"
$GradleHome = Join-Path $Root "gradle"
$GradleUserHome = Join-Path $Root "gradle-home"
$ProjectDir = "D:\code\news"
$GradleDistUrl = "https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip"

function New-Dir($path) {
    if (-not (Test-Path -LiteralPath $path)) {
        New-Item -ItemType Directory -Force -Path $path | Out-Null
    }
}

function Remove-InRoot($path) {
    $full = [System.IO.Path]::GetFullPath($path)
    $rootFull = [System.IO.Path]::GetFullPath($Root)
    if (-not $full.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "refuse to delete outside ${Root}: $full"
    }
    if (Test-Path -LiteralPath $full) {
        Remove-Item -LiteralPath $full -Recurse -Force
    }
}

function Get-Download($urls, $dest) {
    if (Test-Path -LiteralPath $dest) {
        Write-Host "[skip] $dest"
        return
    }
    $part = "$dest.part"
    foreach ($url in $urls) {
        Write-Host "[get ] $url"
        & curl.exe --noproxy "*" -sS -L --fail --retry 3 --retry-delay 5 --connect-timeout 30 -o $part $url
        if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $part)) {
            Move-Item -LiteralPath $part -Destination $dest -Force
            return
        }
        Write-Host "[fail] $url"
        if (Test-Path -LiteralPath $part) { Remove-Item -LiteralPath $part -Force }
    }
    throw "download failed: $dest"
}

function Expand-To($zip, $dest) {
    Remove-InRoot $dest
    New-Dir $dest
    Expand-Archive -LiteralPath $zip -DestinationPath $dest -Force
}

New-Dir $Root
New-Dir $Downloads
New-Dir $Logs
New-Dir $Sdk
New-Dir $GradleUserHome

if (-not $SkipDownloads -and -not $CheckOnly) {
    $jdkZip = Join-Path $Downloads "jdk17.zip"
    Get-Download @(
        "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip",
        "https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_windows-x64_bin.zip",
        "https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip"
    ) $jdkZip
    if (-not (Test-Path -LiteralPath (Join-Path $JdkHome "bin\java.exe"))) {
        $tmp = Join-Path $Root "extract-jdk"
        Expand-To $jdkZip $tmp
        $inner = Get-ChildItem -LiteralPath $tmp -Directory | Select-Object -First 1
        Remove-InRoot $JdkHome
        Move-Item -LiteralPath $inner.FullName -Destination $JdkHome
        Remove-InRoot $tmp
    }

    $ctZip = Join-Path $Downloads "cmdline-tools.zip"
    Get-Download @(
        "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
        "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip"
    ) $ctZip
    $ctLatest = Join-Path $Sdk "cmdline-tools\latest"
    if (-not (Test-Path -LiteralPath (Join-Path $ctLatest "bin\sdkmanager.bat"))) {
        $tmp = Join-Path $Root "extract-ct"
        Expand-To $ctZip $tmp
        Remove-InRoot $ctLatest
        New-Dir (Join-Path $Sdk "cmdline-tools")
        Move-Item -LiteralPath (Join-Path $tmp "cmdline-tools") -Destination $ctLatest
        Remove-InRoot $tmp
    }

    $gradleZip = Join-Path $Downloads "gradle-8.9-bin.zip"
    Get-Download @(
        $GradleDistUrl,
        "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
    ) $gradleZip
    if (-not (Test-Path -LiteralPath (Join-Path $GradleHome "bin\gradle.bat"))) {
        $tmp = Join-Path $Root "extract-gradle"
        Expand-To $gradleZip $tmp
        $inner = Get-ChildItem -LiteralPath $tmp -Directory | Select-Object -First 1
        Remove-InRoot $GradleHome
        Move-Item -LiteralPath $inner.FullName -Destination $GradleHome
        Remove-InRoot $tmp
    }
}

if (Test-Path -LiteralPath (Join-Path $JdkHome "bin\java.exe")) {
    $env:JAVA_HOME = $JdkHome
    $env:Path = (Join-Path $JdkHome "bin") + ";" + $env:Path
}
if (Test-Path -LiteralPath (Join-Path $GradleHome "bin\gradle.bat")) {
    $env:Path = (Join-Path $GradleHome "bin") + ";" + $env:Path
}
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:GRADLE_USER_HOME = $GradleUserHome

Write-Host "=== toolchain ==="
if (Test-Path -LiteralPath (Join-Path $JdkHome "bin\java.exe")) {
    & (Join-Path $JdkHome "bin\java.exe") -version
}
if (Test-Path -LiteralPath (Join-Path $GradleHome "bin\gradle.bat")) {
    & (Join-Path $GradleHome "bin\gradle.bat") --version
}

if (-not $CheckOnly) {
    $sdkManager = Join-Path $Sdk "cmdline-tools\latest\bin\sdkmanager.bat"
    if (-not (Test-Path -LiteralPath $sdkManager)) { throw "sdkmanager not found: $sdkManager" }

    $licenseLog = Join-Path $Logs "sdk-licenses.log"
    Write-Host "=== accept sdk licenses ==="
    $yes = ("y" + [Environment]::NewLine) * 80
    $yes | & $sdkManager --sdk_root=$Sdk --licenses 2>&1 | Out-File -FilePath $licenseLog -Encoding utf8
    Write-Host "licenses exit code: $LASTEXITCODE"
    if ($LASTEXITCODE -ne 0) { throw "sdkmanager --licenses failed" }

    $installLog = Join-Path $Logs "sdk-install.log"
    Write-Host "=== install sdk packages ==="
    & $sdkManager --sdk_root=$Sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0" 2>&1 | Out-File -FilePath $installLog -Encoding utf8
    Write-Host "install exit code: $LASTEXITCODE"
    if ($LASTEXITCODE -ne 0) { throw "sdkmanager install failed" }
    Get-Content -LiteralPath $installLog -Tail 15

    Write-Host "=== gradle wrapper ==="
    $wrapperTmp = Join-Path $Root "wrapper-tmp"
    Remove-InRoot $wrapperTmp
    New-Dir $wrapperTmp
    Set-Content -LiteralPath (Join-Path $wrapperTmp "settings.gradle.kts") -Value 'rootProject.name = "wrapper-generator"' -Encoding ASCII
    Push-Location $wrapperTmp
    & (Join-Path $GradleHome "bin\gradle.bat") wrapper --gradle-version 8.9 --distribution-type bin --gradle-distribution-url $GradleDistUrl
    $wrapperCode = $LASTEXITCODE
    Pop-Location
    Write-Host "wrapper exit code: $wrapperCode"
    if ($wrapperCode -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $wrapperTmp "gradlew.bat"))) {
        throw "gradle wrapper generation failed"
    }

    Copy-Item -LiteralPath (Join-Path $wrapperTmp "gradlew.bat") -Destination $ProjectDir -Force
    Copy-Item -LiteralPath (Join-Path $wrapperTmp "gradlew") -Destination $ProjectDir -Force
    New-Dir (Join-Path $ProjectDir "gradle\wrapper")
    Copy-Item -LiteralPath (Join-Path $wrapperTmp "gradle\wrapper\gradle-wrapper.jar") -Destination (Join-Path $ProjectDir "gradle\wrapper") -Force
    Copy-Item -LiteralPath (Join-Path $wrapperTmp "gradle\wrapper\gradle-wrapper.properties") -Destination (Join-Path $ProjectDir "gradle\wrapper") -Force
    Write-Host "wrapper written to $ProjectDir"

    $sdkDirValue = $Sdk -replace "\\", "/"
    Set-Content -LiteralPath (Join-Path $ProjectDir "local.properties") -Value ("sdk.dir=" + $sdkDirValue) -Encoding ASCII
    Write-Host "local.properties: sdk.dir=$sdkDirValue"
}

Write-Host "=== dependency availability (aliyun mirrors) ==="
$checks = [ordered]@{
    "AGP 8.7.3"                = "https://maven.aliyun.com/repository/google/com/android/tools/build/gradle/8.7.3/gradle-8.7.3.pom"
    "kotlin-compose 2.0.21"    = "https://maven.aliyun.com/repository/public/org/jetbrains/kotlin/plugin/compose/org.jetbrains.kotlin.plugin.compose.gradle.plugin/2.0.21/org.jetbrains.kotlin.plugin.compose.gradle.plugin-2.0.21.pom"
    "KSP 2.0.21-1.0.28"        = "https://maven.aliyun.com/repository/public/com/google/devtools/ksp/symbol-processing-gradle-plugin/2.0.21-1.0.28/symbol-processing-gradle-plugin-2.0.21-1.0.28.pom"
    "compose-bom 2024.12.01"   = "https://maven.aliyun.com/repository/google/androidx/compose/compose-bom/2024.12.01/compose-bom-2024.12.01.pom"
    "room 2.6.1"               = "https://maven.aliyun.com/repository/google/androidx/room/room-runtime/2.6.1/room-runtime-2.6.1.pom"
    "work 2.10.0"              = "https://maven.aliyun.com/repository/google/androidx/work/work-runtime-ktx/2.10.0/work-runtime-ktx-2.10.0.pom"
    "lifecycle 2.8.7"          = "https://maven.aliyun.com/repository/google/androidx/lifecycle/lifecycle-runtime-compose/2.8.7/lifecycle-runtime-compose-2.8.7.pom"
    "core-ktx 1.15.0"          = "https://maven.aliyun.com/repository/google/androidx/core/core-ktx/1.15.0/core-ktx-1.15.0.pom"
    "activity-compose 1.9.3"   = "https://maven.aliyun.com/repository/google/androidx/activity/activity-compose/1.9.3/activity-compose-1.9.3.pom"
    "navigation 2.8.5"         = "https://maven.aliyun.com/repository/google/androidx/navigation/navigation-compose/2.8.5/navigation-compose-2.8.5.pom"
    "datastore-prefs 1.1.1"    = "https://maven.aliyun.com/repository/google/androidx/datastore/datastore-preferences/1.1.1/datastore-preferences-1.1.1.pom"
    "icons-extended (bom)"     = "https://maven.aliyun.com/repository/google/androidx/compose/material/material-icons-extended/1.7.6/material-icons-extended-1.7.6.pom"
    "okhttp 4.12.0"            = "https://maven.aliyun.com/repository/public/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.pom"
    "coroutines-android 1.9.0" = "https://maven.aliyun.com/repository/public/org/jetbrains/kotlinx/kotlinx-coroutines-android/1.9.0/kotlinx-coroutines-android-1.9.0.pom"
    "cmdline-tools zip"        = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
}
foreach ($key in $checks.Keys) {
    $code = & curl.exe --noproxy "*" -s -o NUL -w "%{http_code}" --max-time 25 -L -r 0-512 $checks[$key]
    Write-Host ("{0,-26} {1}" -f $key, $code)
}

Write-Host "=== KSP versions matching Kotlin 2.0.21 ==="
$meta = & curl.exe --noproxy "*" -s --max-time 30 "https://maven.aliyun.com/repository/public/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml"
$versions = [regex]::Matches($meta, '<version>([^<]+)</version>') | ForEach-Object { $_.Groups[1].Value }
$matched = $versions | Where-Object { $_ -like "2.0.21-*" }
if ($matched) { Write-Host ($matched -join ", ") } else { Write-Host "none found" }

if ($Build) {
    Write-Host "=== assembleDebug ==="
    $buildLog = Join-Path $Logs "build.log"
    Push-Location $ProjectDir
    & (Join-Path $ProjectDir "gradlew.bat") --console=plain assembleDebug 2>&1 | Out-File -FilePath $buildLog -Encoding utf8
    $buildCode = $LASTEXITCODE
    Pop-Location
    Write-Host "build exit code: $buildCode"
    Select-String -LiteralPath $buildLog -Pattern "^e: |error: |FAILURE|Execution failed|What went wrong|BUILD " | ForEach-Object { $_.Line } | Select-Object -First 60
    $apk = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path -LiteralPath $apk) {
        Get-Item -LiteralPath $apk | Select-Object FullName, Length, LastWriteTime | Format-List
    }
}

Write-Host "=== done ==="
Write-Host "JDK    : $JdkHome"
Write-Host "SDK    : $Sdk"
Write-Host "Gradle : $GradleHome"
Write-Host "Cache  : $GradleUserHome"
