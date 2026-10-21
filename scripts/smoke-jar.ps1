# Start the packaged JAR twice with isolated data and verify real restart persistence.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
$java = Initialize-ShopJava
$root = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $root 'backend-java/target/online-shopping.jar'
if (!(Test-Path -LiteralPath $jar)) { throw 'Build the JAR first: mvn clean verify' }
$output = Join-Path $root '06_系统测试/results-java'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$temp = Join-Path ([IO.Path]::GetTempPath()) ('shopping-jar-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()
$base = "http://127.0.0.1:$port"
$db = Join-Path $temp 'smoke.db'
$checks = @()
$process = $null
function Assert-Smoke($condition, [string]$description) {
    if (!$condition) { throw "FAIL: $description" }
    $script:checks += [ordered]@{name=$description; status='PASS'}
}
function Start-Smoke([int]$run, [string]$initial) {
    $script:process = Start-Process -FilePath $java -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', "`"$jar`"", "--server.port=$port", '--server.address=127.0.0.1', "`"--shop.db-path=$db`"", "--shop.initial-password=$initial") -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $output "jar-run-$run.log") -RedirectStandardError (Join-Path $output "jar-run-$run.err.log")
    for ($attempt=0; $attempt -lt 80; $attempt++) {
        try { $null = Invoke-RestMethod "$base/api/account" -TimeoutSec 2; return }
        catch { if ($script:process.HasExited) { throw "Java process exited: $($script:process.ExitCode)" }; Start-Sleep -Milliseconds 250 }
    }
    throw 'Java startup timed out'
}
function Stop-Smoke {
    if ($script:process -and !$script:process.HasExited) {
        Stop-Process -Id $script:process.Id
        $script:process.WaitForExit()
    }
    $script:process = $null
}
function Post-Smoke([string]$path, $data, $session) {
    $body = [Text.Encoding]::UTF8.GetBytes(($data | ConvertTo-Json -Compress))
    return Invoke-RestMethod "$base$path" -Method Post -ContentType 'application/json' -Headers @{'X-Requested-With'='ShopApp'} -Body $body -WebSession $session
}
try {
    Start-Smoke 1 'seller12345'
    $homepage = Invoke-WebRequest "$base/" -UseBasicParsing
    Assert-Smoke ($homepage.StatusCode -eq 200 -and $homepage.Content.Contains('/api.js')) 'packaged frontend and API adapter available'
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $null = Post-Smoke '/api/login' @{username='seller';password='seller12345'} $session
    $image = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII='
    $product = Post-Smoke '/api/products' @{name='restart-product';description='persistence';price='0.01';image=$image} $session
    Assert-Smoke ($product.id -gt 0) 'packaged JAR publishes product'
    $buyer = Post-Smoke '/api/intents' @{product_id=$product.id;buyer_name='buyer';contact='13800138000';note=''} $session
    $null = Post-Smoke '/api/trade' @{product_id=$product.id;action='freeze';intent_id=$buyer.id} $session
    $null = Post-Smoke '/api/password' @{old_password='seller12345';new_password='restart12345'} $session
    Stop-Smoke
    Start-Smoke 2 'different12345'
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $null = Post-Smoke '/api/login' @{username='seller';password='restart12345'} $session
    Assert-Smoke $true 'changed password survives process restart and alternate initial password'
    $current = (Invoke-RestMethod "$base/api/product").product
    Assert-Smoke ($current.id -eq $product.id -and $current.status -eq 'frozen' -and $current.price_cents -eq 1) 'product status and exact cents survive restart'
    $trades = Invoke-RestMethod "$base/api/trades" -WebSession $session
    Assert-Smoke ($trades.intents.Count -eq 1 -and $trades.events.Count -eq 1 -and $trades.intents[0].status -eq 'selected') 'intent selection and trade event survive restart'
    $null = Post-Smoke '/api/trade' @{product_id=$product.id;action='success'} $session
    Assert-Smoke ($null -eq (Invoke-RestMethod "$base/api/product").product) 'persisted frozen transaction can complete after restart'
    [ordered]@{time=(Get-Date -Format o); success=$true; checks=$checks; jar_sha256=(Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant()} | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $output 'jar-smoke.json') -Encoding UTF8
    Write-Output "PASS: $($checks.Count) packaged JAR / restart checks"
} finally {
    Stop-Smoke
    # Delete only this script's explicitly created temporary directory.
    $resolvedTemp = [IO.Path]::GetFullPath($temp)
    $expectedParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    if ($resolvedTemp.StartsWith($expectedParent, [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolvedTemp -Leaf).StartsWith('shopping-jar-')) {
        Remove-Item -LiteralPath $resolvedTemp -Force -Recurse
    }
}
