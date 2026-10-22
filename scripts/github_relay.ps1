param(
    [int]$ListenPort = 8443,
    [string]$LogFile = "D:\AndroidDev\logs\github-relay.log",
    [int]$ConnectionTimeoutSeconds = 1800
)

# Minimal HTTP CONNECT proxy.
#
# Why: the local hosts file maps github.com (and friends) to 127.0.0.1, written by an
# accelerator tool (Steam++ / Watt Toolkit) whose local reverse proxy does not work for
# GitHub. This proxy resolves the real IP itself, so it bypasses hosts entirely. When git
# goes through it, the Host header and TLS SNI stay github.com, so encryption is still
# end to end between git and GitHub.
$ErrorActionPreference = "Continue"
$env:HTTP_PROXY = ""
$env:HTTPS_PROXY = ""
$env:ALL_PROXY = ""
$env:NO_PROXY = ""

function Write-Log($message) {
    $line = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $message
    try { Add-Content -LiteralPath $LogFile -Value $line -Encoding UTF8 } catch { }
}

$ipCache = @{}

# The DoH answer for github.com (20.205.243.166) is interfered with on this network.
# These IPs were verified reachable and are preferred; other hosts still use DoH.
$hostOverrides = @{
    "github.com"     = @("140.82.113.3", "20.27.177.113", "140.82.112.3", "20.26.156.215", "20.200.245.247")
    "api.github.com" = @("20.205.243.168")
}

function Resolve-TargetIps([string]$hostName) {
    if ($ipCache.ContainsKey($hostName)) { return $ipCache[$hostName] }
    if ($hostOverrides.ContainsKey($hostName)) {
        $override = $hostOverrides[$hostName]
        $ipCache[$hostName] = $override
        return $override
    }
    $ips = @()
    try {
        $json = & curl.exe --noproxy "*" -s --max-time 10 ("https://dns.alidns.com/resolve?name=$hostName&type=A")
        $ips = [regex]::Matches($json, '"data":"([0-9.]+)"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
    } catch { }
    if (-not $ips -or $ips.Count -eq 0) { $ips = @($hostName) }
    $ipCache[$hostName] = $ips
    return $ips
}

$listener = New-Object -TypeName System.Net.Sockets.TcpListener -ArgumentList @([System.Net.IPAddress]::Loopback, $ListenPort)
$listener.Start()
Write-Host "connect proxy running on 127.0.0.1:$ListenPort"
Write-Log "proxy started on 127.0.0.1:$ListenPort"

while ($true) {
    $client = $listener.AcceptTcpClient()
    $upstream = $null
    try {
        $stream = $client.GetStream()
        $buffer = New-Object byte[] 2048
        $builder = New-Object System.Text.StringBuilder
        $watch = [System.Diagnostics.Stopwatch]::StartNew()

        while ($watch.ElapsedMilliseconds -lt 3000) {
            if ($stream.DataAvailable) {
                $read = $stream.Read($buffer, 0, $buffer.Length)
                if ($read -le 0) { break }
                [void]$builder.Append([System.Text.Encoding]::ASCII.GetString($buffer, 0, $read))
                if ($builder.ToString().Contains([char]13 + [char]10 + [char]13 + [char]10)) { break }
            } else {
                Start-Sleep -Milliseconds 20
            }
        }
        $watch.Stop()

        $request = $builder.ToString()
        $match = [regex]::Match($request, "(?im)^CONNECT\s+([^:\s]+):(\d+)")
        if (-not $match.Success) {
            $bad = "HTTP/1.1 405 Only CONNECT is supported`r`nConnection: close`r`n`r`n"
            $badBytes = [System.Text.Encoding]::ASCII.GetBytes($bad)
            $stream.Write($badBytes, 0, $badBytes.Length)
            $stream.Flush()
            Write-Log "rejected non-CONNECT request"
            continue
        }

        $targetHost = $match.Groups[1].Value
        $targetPort = [int]$match.Groups[2].Value
        $ips = Resolve-TargetIps $targetHost

        foreach ($ip in $ips) {
            try {
                $upstream = New-Object System.Net.Sockets.TcpClient
                $connectTask = $upstream.ConnectAsync($ip, $targetPort)
                if ($connectTask.Wait(8000) -and $upstream.Connected) { break }
                $upstream.Close()
                $upstream = $null
            } catch {
                $upstream = $null
            }
        }

        if (-not $upstream) {
            $fail = "HTTP/1.1 502 Bad Gateway`r`nConnection: close`r`n`r`n"
            $failBytes = [System.Text.Encoding]::ASCII.GetBytes($fail)
            $stream.Write($failBytes, 0, $failBytes.Length)
            $stream.Flush()
            Write-Log "upstream connect failed: ${targetHost}:${targetPort}"
            continue
        }

        $ok = "HTTP/1.1 200 Connection established`r`n`r`n"
        $okBytes = [System.Text.Encoding]::ASCII.GetBytes($ok)
        $stream.Write($okBytes, 0, $okBytes.Length)
        $stream.Flush()
        Write-Log "tunnel ${targetHost}:${targetPort} -> $ip"

        $upstreamStream = $upstream.GetStream()
        $toUpstream = $stream.CopyToAsync($upstreamStream)
        $toClient = $upstreamStream.CopyToAsync($stream)
        [void][System.Threading.Tasks.Task]::WaitAll(
            [System.Threading.Tasks.Task[]]@($toUpstream, $toClient),
            ($ConnectionTimeoutSeconds * 1000)
        )
    } catch {
        Write-Log ("error: " + $_.Exception.Message)
    } finally {
        try { $client.Close() } catch { }
        if ($upstream) { try { $upstream.Close() } catch { } }
    }
}
