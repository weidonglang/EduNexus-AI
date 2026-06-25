param(
    [string]$Version = "1.0",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$FrontendDir = Join-Path $Root "frontend"
$ReleaseRoot = Join-Path $Root "release"
$PackageName = "Academic-Nexus-$Version"
$PackageDir = Join-Path $ReleaseRoot $PackageName
$ZipPath = Join-Path $ReleaseRoot "$PackageName.zip"

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    Write-Host ""
    Write-Host "==> $Name" -ForegroundColor Cyan
    & $Action
}

function Invoke-NativeCommand {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @()
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $FilePath $($Arguments -join ' ')"
    }
}

function Copy-RequiredFile {
    param(
        [string]$Source,
        [string]$Destination
    )
    if (-not (Test-Path -LiteralPath $Source)) {
        throw "Required file not found: $Source"
    }
    Copy-Item -LiteralPath $Source -Destination $Destination -Force
}

Invoke-Step "Build frontend assets" {
    Push-Location $FrontendDir
    try {
        Invoke-NativeCommand "npm" @("install")
        Invoke-NativeCommand "npm" @("run", "build")
    } finally {
        Pop-Location
    }
}

Invoke-Step "Package main Spring Boot application" {
    Push-Location $Root
    try {
        if ($SkipTests) {
            Invoke-NativeCommand ".\mvnw.cmd" @("clean", "package", "-DskipTests")
        } else {
            Invoke-NativeCommand ".\mvnw.cmd" @("clean", "package")
        }
    } finally {
        Pop-Location
    }
}

Invoke-Step "Package AI service" {
    Push-Location $Root
    try {
        if ($SkipTests) {
            Invoke-NativeCommand ".\mvnw.cmd" @("-f", "ai-service/pom.xml", "clean", "package", "-DskipTests")
        } else {
            Invoke-NativeCommand ".\mvnw.cmd" @("-f", "ai-service/pom.xml", "clean", "package")
        }
    } finally {
        Pop-Location
    }
}

Invoke-Step "Assemble release directory" {
    if (Test-Path -LiteralPath $PackageDir) {
        Remove-Item -LiteralPath $PackageDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $PackageDir | Out-Null

    Copy-RequiredFile -Source (Join-Path $Root "target/tianshiwebside-0.0.1-SNAPSHOT.jar") -Destination (Join-Path $PackageDir "academic-nexus-web.jar")
    Copy-RequiredFile -Source (Join-Path $Root "ai-service/target/tianshi-ai-service-0.0.1-SNAPSHOT.jar") -Destination (Join-Path $PackageDir "academic-nexus-ai-service.jar")
    Copy-RequiredFile -Source (Join-Path $Root "README.md") -Destination (Join-Path $PackageDir "README.md")
    Copy-RequiredFile -Source (Join-Path $Root "docs/startup-guide.md") -Destination (Join-Path $PackageDir "startup-guide.md")

    @'
# Academic-Nexus release environment
SPRING_PROFILES_ACTIVE=demo
SERVER_PORT=8080
AI_SERVICE_PORT=8090
AI_SERVICE_URL=http://localhost:8090

DB_URL=jdbc:mysql://localhost:3306/tianshiwebside?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=123123

REDIS_HOST=localhost
REDIS_PORT=6379
UPLOAD_ROOT=uploads

OLLAMA_ENABLED=false
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_CHAT_MODEL=qwen3:8b
OLLAMA_SQL_MODEL=qwen2.5-coder:7b
'@ | Set-Content -Path (Join-Path $PackageDir ".env.example") -Encoding UTF8

    @'
param(
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"
$BaseDir = $PSScriptRoot
$EnvPath = Join-Path $BaseDir $EnvFile

if (-not (Test-Path -LiteralPath $EnvPath)) {
    $EnvPath = Join-Path $BaseDir ".env.example"
}

Get-Content -LiteralPath $EnvPath | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $name, $value = $line.Split("=", 2)
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), "Process")
    }
}

if (-not $env:AI_SERVICE_URL -and $env:AI_SERVICE_PORT) {
    $env:AI_SERVICE_URL = "http://localhost:$($env:AI_SERVICE_PORT)"
}

Write-Host "Starting Academic-Nexus AI service on port $env:AI_SERVICE_PORT"
$ai = Start-Process -FilePath "java" -ArgumentList @("-jar", (Join-Path $BaseDir "academic-nexus-ai-service.jar")) -PassThru -WindowStyle Hidden

try {
    Write-Host "Starting Academic-Nexus web application on port $env:SERVER_PORT"
    java -jar (Join-Path $BaseDir "academic-nexus-web.jar")
} finally {
    if ($ai -and -not $ai.HasExited) {
        Stop-Process -Id $ai.Id
    }
}
'@ | Set-Content -Path (Join-Path $PackageDir "start-release.ps1") -Encoding UTF8

    @'
@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-release.ps1"
endlocal
'@ | Set-Content -Path (Join-Path $PackageDir "start-release.bat") -Encoding ASCII

    @"
# Academic-Nexus $Version

This package contains the built Spring Boot web application and the companion AI service.

## Quick start

1. Install Java 17.
2. Start MySQL and Redis.
3. Create a database named `tianshiwebside`.
4. Copy `.env.example` to `.env` and adjust database or Ollama settings if needed.
5. Run `start-release.bat` on Windows, or `powershell -ExecutionPolicy Bypass -File .\start-release.ps1`.

Default demo accounts use password `123456`:

- `admin001`
- `teacher001`
- `student001`

The web application starts at `http://localhost:8080`.
"@ | Set-Content -Path (Join-Path $PackageDir "README-release.md") -Encoding UTF8
}

Invoke-Step "Create zip archive" {
    if (Test-Path -LiteralPath $ZipPath) {
        Remove-Item -LiteralPath $ZipPath -Force
    }
    Compress-Archive -Path (Join-Path $PackageDir "*") -DestinationPath $ZipPath -Force
    Write-Host "Release package: $ZipPath" -ForegroundColor Green
}
