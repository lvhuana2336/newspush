$ErrorActionPreference = "Continue"

$pidFile = "D:\AndroidDev\github-relay.pid"
$stopped = 0

if (Test-Path -LiteralPath $pidFile) {
    $relayPid = (Get-Content -LiteralPath $pidFile -First 1).Trim()
    if ($relayPid) {
        if (Get-Process -Id $relayPid -ErrorAction SilentlyContinue) {
            Stop-Process -Id $relayPid -Force
            $stopped = $stopped + 1
            Write-Host ("stopped relay pid " + $relayPid)
        }
    }
}

foreach ($p in (Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*github_relay.ps1*" -and $_.ProcessId -ne $PID })) {
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    $stopped = $stopped + 1
    Write-Host ("stopped leftover relay pid " + $p.ProcessId)
}

Write-Host ("stopped processes: " + $stopped)
