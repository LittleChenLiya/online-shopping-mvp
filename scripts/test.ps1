$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
$null = Initialize-ShopJava
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    try { Invoke-ShopMaven -MavenArguments @('clean', 'verify') }
    finally {
        if (Test-Path 'backend-java/target/surefire-reports/TEST-*.xml') {
            & (Join-Path $PSScriptRoot 'record-test-results.ps1')
        }
    }
} finally { Pop-Location }
