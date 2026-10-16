param(
    [string]$Task = "assembleDebug",
    [switch]$Install
)

$ErrorActionPreference = "Continue"

$EnvRoot = "D:\AndroidDev"
$env:JAVA_HOME = Join-Path $EnvRoot "jdk"
$env:ANDROID_HOME = Join-Path $EnvRoot "android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $EnvRoot "gradle-home"
$env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + (Join-Path $EnvRoot "gradle\bin") + ";" + $env:Path

Push-Location $PSScriptRoot
& (Join-Path $PSScriptRoot "gradlew.bat") --console=plain $Task
$code = $LASTEXITCODE
Pop-Location

$apkRoot = Join-Path $PSScriptRoot "app\build\outputs\apk"
$apks = @()
if (Test-Path -LiteralPath $apkRoot) {
    $apks = Get-ChildItem -LiteralPath $apkRoot -Recurse -Filter *.apk -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending
}

if ($code -eq 0) {
    foreach ($a in $apks) {
        $a | Select-Object FullName, Length, LastWriteTime | Format-List
    }

    if ($Install -and $apks.Count -gt 0) {
        $adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
        Write-Host "=== adb devices ==="
        & $adb devices
        Write-Host "=== install ==="
        & $adb install -r $apks[0].FullName
    }
}

Write-Host "exit code: $code"
exit $code
