# Shared by build, start and system-test scripts; prefer the java actually on PATH.
$ErrorActionPreference = 'Stop'
function Initialize-ShopJava {
    $candidates = @()
    if ($env:SHOP_JAVA_HOME) { $candidates += Join-Path $env:SHOP_JAVA_HOME 'bin/java.exe' }
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) { $candidates += $javaCommand.Source }
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin/java.exe' }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (!(Test-Path -LiteralPath $candidate)) { continue }
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $version = (& $candidate -XshowSettings:properties -version 2>&1 | Out-String)
        $ErrorActionPreference = $previousPreference
        if ($version -match 'version "(?<major>\d+)(?:\.(?<minor>\d+))?') {
            $major = [int]$Matches.major
            if ($major -eq 1) { $major = [int]$Matches.minor }
            if ($major -ge 17) {
                if ($version -match '(?m)^\s*java\.home\s*=\s*(.+)$') {
                    $env:JAVA_HOME = $Matches[1].Trim()
                } else {
                    $env:JAVA_HOME = Split-Path (Split-Path $candidate -Parent) -Parent
                }
                $env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"
                return Join-Path $env:JAVA_HOME 'bin/java.exe'
            }
        }
    }
    throw 'JDK 17+ was not found. Install JDK 17 and set JAVA_HOME or SHOP_JAVA_HOME.'
}
function Invoke-ShopMaven {
    param([string[]]$MavenArguments)
    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (!$maven) { throw 'Maven 3.6.3+ was not found. Add its bin directory to PATH.' }
    $arguments = @('-B', '-Dfile.encoding=UTF-8')
    if ($env:SHOP_MAVEN_REPO) { $arguments += "-Dmaven.repo.local=$env:SHOP_MAVEN_REPO" }
    & $maven.Source @arguments @MavenArguments
    if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE" }
}
