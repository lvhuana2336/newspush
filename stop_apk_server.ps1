$ErrorActionPreference = "Continue"

$pidFile = "D:\AndroidDev\apk-server.pid"
$stopped = 0

if (Test-Path -LiteralPath $pidFile) {
    $serverPid = (Get-Content -LiteralPath $pidFile -First 1).Trim()
    if ($serverPid) {
        $proc = Get-Process -Id $serverPid -ErrorAction SilentlyContinue
        if ($proc) {
            Stop-Process -Id $serverPid -Force
            $stopped = $stopped + 1
            Write-Host ("stopped server pid " + $serverPid)
        }
    }
}

foreach ($p in (Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*serve_apk.ps1*" -and $_.ProcessId -ne $PID })) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    $stopped = $stopped + 1
    Write-Host ("stopped leftover server pid " + $p.ProcessId)
}

Write-Host ("stopped processes: " + $stopped)
