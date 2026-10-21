$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$record = Join-Path $root 'logs/server-process.json'
if (!(Test-Path -LiteralPath $record)) { Write-Host 'No running service record.'; exit 0 }
$saved = Get-Content -LiteralPath $record -Raw -Encoding UTF8 | ConvertFrom-Json
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$($saved.pid)" -ErrorAction SilentlyContinue
if ($process -and $process.Name -eq 'java.exe' -and $process.CommandLine.Contains([string]$saved.jar)) {
    Stop-Process -Id $saved.pid
    Write-Host 'Java shopping system stopped.'
} else { Write-Host 'The recorded Java service is already stopped.' }
