# Summarize real Surefire XML and source hashes; never create fabricated pass records.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$reports = Join-Path $root 'backend-java/target/surefire-reports'
$output = Join-Path $root '06_系统测试/results-java'
$files = @(Get-ChildItem -LiteralPath $reports -Filter 'TEST-*.xml')
if (!$files.Count) { throw 'No Surefire XML found. Run Java tests first.' }
New-Item -ItemType Directory -Force -Path $output | Out-Null
$cases = @()
foreach ($file in $files) {
    [xml]$report = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    foreach ($case in $report.testsuite.testcase) {
        $state = 'PASS'
        if ($case.failure) { $state = 'FAIL' }
        elseif ($case.error) { $state = 'ERROR' }
        elseif ($case.skipped) { $state = 'SKIP' }
        $cases += [ordered]@{class=$case.classname; name=$case.name; status=$state; seconds=$case.time}
    }
    Copy-Item -LiteralPath $file.FullName -Destination $output -Force
}
$hashes = [ordered]@{}
foreach ($file in (Get-ChildItem (Join-Path $root 'backend-java/src'),(Join-Path $root '01_前端界面') -File -Recurse | Where-Object { $_.Extension -in '.java','.properties','.js','.html','.css','.sqlite' } | Sort-Object FullName)) {
    $relative = $file.FullName.Substring($root.Length + 1).Replace('\','/')
    $hashes[$relative] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}
$buildFiles = @('pom.xml','backend-java/pom.xml')
foreach ($relative in $buildFiles) {
    $hashes[$relative] = (Get-FileHash -LiteralPath (Join-Path $root $relative) -Algorithm SHA256).Hash.ToLowerInvariant()
}
$summary = [ordered]@{time=(Get-Date -Format o); executor='Codex'; mode='local Java automated tests'; total=$cases.Count; passed=@($cases | Where-Object status -eq 'PASS').Count; cases=$cases; input_sha256=$hashes}
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $output 'test-results.json') -Encoding UTF8
$lines = @('# Java 系统测试执行报告', '', "执行时间：$($summary.time)", "执行方式：$($summary.mode)；操作：$($summary.executor)。", '', "共 $($summary.total) 个测试，通过 $($summary.passed) 个。", '', '结果来自 Surefire XML；源码 SHA-256 见 test-results.json。临时数据库不使用正式 data/shop.db。', '', '| 类 / 测试 | 结果 |', '|---|---|')
foreach ($case in $cases) { $lines += "| $($case.class).$($case.name) | $($case.status) |" }
$lines += @('', '此报告验证 Java HTTP/API、数据和安全边界；浏览器验收结果另行记录。Docker 和 GitHub CI 尚未在本机执行。')
$lines | Set-Content -LiteralPath (Join-Path $output '测试报告.md') -Encoding UTF8
Write-Output "Test report: $output"
