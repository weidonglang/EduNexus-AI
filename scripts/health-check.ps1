param(
    [string]$BackendUrl = "http://localhost:8088",
    [string]$FrontendUrl = "http://localhost:5173",
    [string]$NacosUrl = "http://localhost:8848/nacos",
    [string]$Version = "v1.4.0-final-polish",
    [string]$ReportDir = "reports"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ReportPath = Join-Path $Root $ReportDir
New-Item -ItemType Directory -Path $ReportPath -Force | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$mdPath = Join-Path $ReportPath "health-check-$stamp.md"
$jsonPath = Join-Path $ReportPath "health-check-$stamp.json"
$results = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail,
        [string]$Suggestion = ""
    )
    $results.Add([ordered]@{
        name = $Name
        status = $Status
        detail = $Detail
        suggestion = $Suggestion
    })
}

function Test-Http {
    param([string]$Name, [string]$Url)
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
        Add-Check $Name "PASS" "HTTP $($response.StatusCode) $Url"
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        if ($status -eq 401 -or $status -eq 403) {
            Add-Check $Name "PASS" "HTTP $status $Url, service is reachable and protected"
        } else {
            Add-Check $Name "FAIL" "Cannot reach $Url. $($_.Exception.Message)" "Start the service or adjust the URL."
        }
    }
}

function Test-Port {
    param([string]$Name, [string]$HostName, [int]$Port)
    try {
        $ok = (Test-NetConnection -ComputerName $HostName -Port $Port -InformationLevel Quiet)
        if ($ok) {
            Add-Check $Name "PASS" "$HostName`:$Port is reachable"
        } else {
            Add-Check $Name "WARN" "$HostName`:$Port is not reachable" "Start the related Docker service if this environment needs it."
        }
    } catch {
        Add-Check $Name "WARN" "Port check failed for $HostName`:$Port. $($_.Exception.Message)" "Check firewall and Docker Desktop state."
    }
}

Test-Http "Frontend" $FrontendUrl
Test-Http "Backend API" "$BackendUrl/api/ai/status"
Test-Http "Admin health endpoint" "$BackendUrl/api/admin/system-health"
Test-Http "Nacos console" $NacosUrl
Test-Port "MySQL" "localhost" 3306
Test-Port "Redis" "localhost" 6379
Test-Port "Nacos" "localhost" 8848

$composePath = Join-Path $Root "docker-compose.yml"
if (Test-Path -LiteralPath $composePath) {
    Add-Check "Docker Compose file" "PASS" "docker-compose.yml exists"
} else {
    Add-Check "Docker Compose file" "FAIL" "docker-compose.yml missing" "Restore the compose file before release."
}

$uploadRoot = Join-Path $Root "uploads"
try {
    New-Item -ItemType Directory -Path $uploadRoot -Force | Out-Null
    $probe = Join-Path $uploadRoot ".health-check"
    Set-Content -LiteralPath $probe -Value $stamp -Encoding UTF8
    Remove-Item -LiteralPath $probe -Force
    Add-Check "Upload directory" "PASS" "$uploadRoot is writable"
} catch {
    Add-Check "Upload directory" "FAIL" "$uploadRoot is not writable. $($_.Exception.Message)" "Grant write permission or set UPLOAD_ROOT."
}

$releaseZip = Join-Path $Root "release\Academic-Nexus-1.4.0.zip"
if (Test-Path -LiteralPath $releaseZip) {
    Add-Check "Release zip" "PASS" $releaseZip
} else {
    Add-Check "Release zip" "WARN" "$releaseZip not found" "Run scripts/build-release.ps1 -Version 1.4.0 before publishing."
}

$overall = if ($results.status -contains "FAIL") { "FAIL" } elseif ($results.status -contains "WARN") { "WARN" } else { "PASS" }
$report = [ordered]@{
    version = $Version
    generatedAt = (Get-Date).ToString("o")
    overall = $overall
    backendUrl = $BackendUrl
    frontendUrl = $FrontendUrl
    checks = $results
}

$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine("# Academic-Nexus Health Check")
[void]$md.AppendLine()
[void]$md.AppendLine("- Version: $Version")
[void]$md.AppendLine("- Generated: $($report.generatedAt)")
[void]$md.AppendLine("- Overall: $overall")
[void]$md.AppendLine()
[void]$md.AppendLine("| Check | Status | Detail | Suggestion |")
[void]$md.AppendLine("| --- | --- | --- | --- |")
foreach ($item in $results) {
    [void]$md.AppendLine("| $($item.name) | $($item.status) | $($item.detail.Replace('|','/')) | $($item.suggestion.Replace('|','/')) |")
}
$md.ToString() | Set-Content -LiteralPath $mdPath -Encoding UTF8

Write-Host "Health check overall: $overall"
Write-Host "Markdown report: $mdPath"
Write-Host "JSON report: $jsonPath"
