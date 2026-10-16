param(
    [Parameter(Mandatory = $true)][string]$RepoUrl,
    [string]$UserName = "lvhuana2336",
    [string]$TokenFile = "D:\AndroidDev\github-token.txt",
    [string]$Branch = "main"
)

$ErrorActionPreference = "Continue"
$env:GIT_TERMINAL_PROMPT = "0"

if (-not (Test-Path -LiteralPath $TokenFile)) {
    throw "token file not found: $TokenFile (put a single-line personal access token there)"
}
$token = ((Get-Content -LiteralPath $TokenFile -TotalCount 1) + "").Trim()
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "token file is empty: $TokenFile"
}

# 用户名写在 URL 里，密码交给 GIT_ASKPASS，
# 这样 token 不会出现在命令行参数、URL 或日志输出中。
$askpass = Join-Path $env:TEMP "news-push-askpass.cmd"
Set-Content -LiteralPath $askpass -Value ("@echo off" + [Environment]::NewLine + "type `"$TokenFile`"") -Encoding ASCII
$env:GIT_ASKPASS = $askpass

$repoFull = $RepoUrl.Trim().TrimEnd("/")
if (-not $repoFull.EndsWith(".git")) { $repoFull = "$repoFull.git" }
$authedUrl = $repoFull -replace "^https://", "https://$UserName@"

$remotes = & git -C $PSScriptRoot remote
if ($remotes -contains "origin") {
    & git -C $PSScriptRoot remote set-url origin $repoFull
} else {
    & git -C $PSScriptRoot remote add origin $repoFull
}
Write-Host "remote origin -> $repoFull"

Write-Host "pushing $Branch ..."
& git -C $PSScriptRoot push $authedUrl $Branch
$code = $LASTEXITCODE

Remove-Item -LiteralPath $askpass -Force -ErrorAction SilentlyContinue
$env:GIT_ASKPASS = $null

if ($code -eq 0) {
    Write-Host "done: pushed to $repoFull"
} else {
    Write-Host "push failed, exit code $code"
}
exit $code
