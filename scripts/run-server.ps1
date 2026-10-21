# Keep Java output out of the terminal pipeline: a paused terminal must not block HTTP.
function Start-ShopServer {
    param([string]$Java, [string]$Jar, [int]$Port=8000, [string]$BindAddress='127.0.0.1', [switch]$NoBrowser)
    $logs = Join-Path (Get-Location).Path 'logs'
    New-Item -ItemType Directory -Force -Path $logs | Out-Null
    $process = Start-Process -FilePath $Java -ArgumentList @('-Dfile.encoding=UTF-8','-jar',"`"$Jar`"", "--server.port=$Port", "--server.address=$BindAddress") -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'server.log') -RedirectStandardError (Join-Path $logs 'server-error.log')
    [ordered]@{pid=$process.Id; jar=$Jar} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logs 'server-process.json') -Encoding UTF8
    $hostAddress = if ($BindAddress -eq '0.0.0.0') { '127.0.0.1' } else { $BindAddress }
    $url = "http://${hostAddress}:$Port"
    try {
        $ready = $false
        for ($attempt=0; $attempt -lt 100; $attempt++) {
            if ($process.HasExited) { throw "Java exited. Read $logs/server.log and server-error.log" }
            try {
                $request = [Net.WebRequest]::Create("$url/api/account")
                $request.Proxy = $null
                $request.Timeout = 1000
                $response = $request.GetResponse()
                try { $ready = ([int]$response.StatusCode -eq 200) } finally { $response.Close() }
            } catch { Start-Sleep -Milliseconds 250 }
            if ($ready) { break }
        }
        if (!$ready) { throw "Startup timed out. Read $logs/server.log" }
        if (!$NoBrowser) { Start-Process $url }
        Write-Host "System ready: $url"
        Write-Host "Keep this window open. Press Ctrl+C to stop. Logs: $logs"
        while (!$process.HasExited) { Start-Sleep -Seconds 1 }
        if ($process.ExitCode -ne 0) { throw "Java exited with code $($process.ExitCode). Read $logs/server.log" }
    } finally {
        if (!$process.HasExited) { Stop-Process -Id $process.Id; $process.WaitForExit() }
    }
}
