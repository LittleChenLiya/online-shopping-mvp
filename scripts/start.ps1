param([int]$Port = 8000, [string]$BindAddress = '127.0.0.1', [switch]$SkipBuild, [switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
. (Join-Path $PSScriptRoot 'run-server.ps1')
$java = Initialize-ShopJava
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    if (!$SkipBuild) { Invoke-ShopMaven -MavenArguments @('-f', 'backend-java/pom.xml', '-DskipTests', 'package') }
    $jar = Join-Path $root 'backend-java/target/online-shopping.jar'
    if (!(Test-Path -LiteralPath $jar)) { throw 'Build the Java application first.' }
    Start-ShopServer -Java $java -Jar $jar -Port $Port -BindAddress $BindAddress -NoBrowser:$NoBrowser

} finally { Pop-Location }
