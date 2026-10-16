param(
    [int]$Port = 8000,
    [string]$ApkPath = "",
    [switch]$NoBuild
)

$ErrorActionPreference = "Continue"
$ProjectDir = $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"
}

if (-not $NoBuild) {
    Write-Host "=== build apk ==="
    & (Join-Path $ProjectDir "build_apk.ps1")
}
if (-not (Test-Path -LiteralPath $ApkPath)) {
    throw "apk not found: $ApkPath"
}

$apk = Get-Item -LiteralPath $ApkPath
$sizeMb = [math]::Round($apk.Length / 1MB, 1)
$downloadName = "newspush-debug.apk"

# Page text lives in an UTF-8 html file so this script stays ASCII-only:
# Windows PowerShell 5.1 parses .ps1 as GBK by default and would break on non-ASCII literals.
$templatePath = Join-Path $ProjectDir "scripts\apk_page.html"
$html = [System.IO.File]::ReadAllText($templatePath, [System.Text.Encoding]::UTF8)
$html = $html.Replace("__FILE__", $downloadName).Replace("__SIZE__", "$sizeMb")
$htmlBytes = [System.Text.Encoding]::UTF8.GetBytes($html)

$addresses = @()
foreach ($c in (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue)) {
    if ($c.IPAddress -like "127.*" -or $c.IPAddress -like "169.254.*") { continue }
    $isVirtual = $false
    if ($c.InterfaceAlias -match "vEthernet|VMware|VirtualBox|Hyper-V|Docker|WSL|Loopback|Bluetooth") { $isVirtual = $true }
    $addresses += [pscustomobject]@{ Alias = $c.InterfaceAlias; IP = $c.IPAddress; Virtual = $isVirtual }
}

$listener = New-Object -TypeName System.Net.Sockets.TcpListener -ArgumentList @([System.Net.IPAddress]::Any, $Port)
$listener.Start()

Write-Host ""
Write-Host "APK server started on port $Port."
Write-Host "Connect the phone to the same Wi-Fi, then open one of these URLs in the phone browser:"
foreach ($a in $addresses) {
    if ($a.Virtual) {
        Write-Host ("  http://{0}:{1}/   [{2}] (virtual adapter, usually not this one)" -f $a.IP, $Port, $a.Alias)
    } else {
        Write-Host ("  http://{0}:{1}/   [{2}]" -f $a.IP, $Port, $a.Alias)
    }
}
Write-Host "Press Ctrl+C to stop the server."
Write-Host ""

$ruleName = "NewsPush APK Debug Server $Port"
$ruleFound = $false
try {
    $existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction Stop
    if ($existing) { $ruleFound = $true }
} catch {
    $ruleFound = $false
}
if (-not $ruleFound) {
    Write-Host "NOTE: could not find an inbound firewall rule for port $Port." -ForegroundColor Yellow
    Write-Host "If the phone cannot open the page above, run this once in an ADMIN PowerShell:" -ForegroundColor Yellow
    Write-Host ('  New-NetFirewallRule -DisplayName "{0}" -Direction Inbound -Action Allow -Protocol TCP -LocalPort {1} -Profile Any' -f $ruleName, $Port) -ForegroundColor Yellow
    Write-Host ""
}

while ($true) {
    $client = $listener.AcceptTcpClient()
    try {
        $stream = $client.GetStream()
        $buffer = New-Object byte[] 4096
        $builder = New-Object System.Text.StringBuilder
        $watch = [System.Diagnostics.Stopwatch]::StartNew()

        while ($watch.ElapsedMilliseconds -lt 3000) {
            if ($stream.DataAvailable) {
                $read = $stream.Read($buffer, 0, $buffer.Length)
                if ($read -le 0) { break }
                [void]$builder.Append([System.Text.Encoding]::ASCII.GetString($buffer, 0, $read))
                if ($builder.ToString().Contains("`r`n`r`n")) { break }
            } else {
                Start-Sleep -Milliseconds 20
            }
        }
        $watch.Stop()

        $request = $builder.ToString()
        $method = "GET"
        $path = "/"
        if ($request -match "^(GET|HEAD)\s+(\S+)") {
            $method = $matches[1]
            $path = $matches[2]
        }
        Write-Host ("{0} {1}" -f $method, $path)

        if ($path -eq "/" -or $path -eq "/index.html") {
            $header = "HTTP/1.1 200 OK`r`nContent-Type: text/html; charset=utf-8`r`nContent-Length: $($htmlBytes.Length)`r`nCache-Control: no-store`r`nConnection: close`r`n`r`n"
            $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($header)
            $stream.Write($headerBytes, 0, $headerBytes.Length)
            if ($method -ne "HEAD") { $stream.Write($htmlBytes, 0, $htmlBytes.Length) }
        } else {
            # re-read on every request so a freshly rebuilt apk is served with the right length
            $current = Get-Item -LiteralPath $apk.FullName
            $header = "HTTP/1.1 200 OK`r`nContent-Type: application/vnd.android.package-archive`r`nContent-Length: $($current.Length)`r`nContent-Disposition: attachment; filename=`"$downloadName`"`r`nAccept-Ranges: none`r`nCache-Control: no-store`r`nConnection: close`r`n`r`n"
            $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($header)
            $stream.Write($headerBytes, 0, $headerBytes.Length)
            if ($method -ne "HEAD") {
                $fileStream = [System.IO.File]::OpenRead($current.FullName)
                try { $fileStream.CopyTo($stream, 1MB) } finally { $fileStream.Close() }
            }
        }
        $stream.Flush()
    } catch {
        Write-Host ("request failed: " + $_.Exception.Message) -ForegroundColor Red
    } finally {
        $client.Close()
    }
}
