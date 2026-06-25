$ErrorActionPreference = "Stop"

$ContainerName = "tianshi-redis"
$ImageName = if ($env:REDIS_IMAGE) { $env:REDIS_IMAGE } else { "redis:7-alpine" }
$Port = "6379"

# Check local Redis first.
# This supports Redis installed on Windows, Redis exposed by WSL, or Redis started by another tool.
# If localhost:6379 is reachable, Spring Boot can use it directly and Docker Redis is not required.
$localRedis = Test-NetConnection localhost -Port $Port -WarningAction SilentlyContinue
if ($localRedis.TcpTestSucceeded) {
    Write-Host "Redis is already reachable: localhost:$Port" -ForegroundColor Green
    Write-Host "Detected an existing Redis service, so Docker Redis will not be started."
    Write-Host "Now restart the Spring Boot backend so it reconnects to Redis."
    exit 0
}

function Invoke-Docker {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )
    & docker @Arguments
    return $LASTEXITCODE
}

function Test-DockerReady {
    docker version --format "Docker server {{.Server.Version}}" *> $null
    return $LASTEXITCODE -eq 0
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Docker is not installed or not in PATH." -ForegroundColor Red
    exit 1
}

if (-not (Test-DockerReady)) {
    Write-Host "Docker Desktop is not running. Please start Docker Desktop first, then run this script again." -ForegroundColor Yellow
    exit 1
}

$existing = docker ps -a --filter "name=^/$ContainerName$" --format "{{.Names}}"
if (-not $existing) {
    Write-Host "Creating Redis container $ContainerName on port $Port..." -ForegroundColor Cyan
    Write-Host "Checking Redis image: $ImageName"

    $hasImage = $false
    & docker image inspect $ImageName | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $hasImage = $true
        Write-Host "Using local Redis image: $ImageName" -ForegroundColor Green
    }

    if (-not $hasImage) {
        $pullExit = Invoke-Docker @("pull", $ImageName)
        if ($pullExit -ne 0) {
            $localRedisImages = docker images --format "{{.Repository}}:{{.Tag}}" |
                    Where-Object { $_ -match "redis" -and $_ -notmatch "<none>" } |
                    Select-Object -First 1

            if ($localRedisImages) {
                $ImageName = $localRedisImages
                Write-Host ""
                Write-Host "Docker cannot download the configured Redis image, but found a local Redis image:" -ForegroundColor Yellow
                Write-Host "  $ImageName"
                Write-Host "Using this local image instead." -ForegroundColor Green
            } else {
                Write-Host ""
                Write-Host "Docker cannot download Redis image: $ImageName" -ForegroundColor Red
                Write-Host "Usually this means Docker Desktop has no internet access, Docker Hub is blocked, or a proxy/mirror is required." -ForegroundColor Yellow
                Write-Host "Try this command manually after fixing Docker network:" -ForegroundColor Yellow
                Write-Host "  docker pull $ImageName"
                Write-Host ""
                Write-Host "You can also use another reachable Redis image before running start-all.bat:" -ForegroundColor Yellow
                Write-Host "  set REDIS_IMAGE=your-registry/redis:7-alpine"
                Write-Host "  start-all.bat"
                Write-Host ""
                Write-Host "The application can still run without Redis. It will use database fallback."
                exit 1
            }
        }
    }

    $runExit = Invoke-Docker @("run", "-d", "--name", $ContainerName, "-p", "${Port}:6379", $ImageName)
    if ($runExit -ne 0) {
        Write-Host ""
        Write-Host "Docker failed to create Redis container." -ForegroundColor Red
        Write-Host "If port 6379 is already used, close the other Redis service or change REDIS_PORT." -ForegroundColor Yellow
        Write-Host "Useful checks:"
        Write-Host "  docker ps -a"
        Write-Host "  Test-NetConnection localhost -Port $Port"
        exit 1
    }
} else {
    $running = docker ps --filter "name=^/$ContainerName$" --format "{{.Names}}"
    if (-not $running) {
        Write-Host "Starting Redis container $ContainerName..." -ForegroundColor Cyan
        $startExit = Invoke-Docker @("start", $ContainerName)
        if ($startExit -ne 0) {
            $runningAfterRetry = docker ps --filter "name=^/$ContainerName$" --format "{{.Names}}"
            if ($runningAfterRetry) {
                Write-Host "Redis container $ContainerName is running after retry." -ForegroundColor Green
            } else {
                Write-Host "Docker failed to start Redis container $ContainerName." -ForegroundColor Red
                Write-Host "Try: docker logs $ContainerName"
                exit 1
            }
        }
    } else {
        Write-Host "Redis container $ContainerName is already running." -ForegroundColor Green
    }
}

Start-Sleep -Seconds 1
$test = Test-NetConnection localhost -Port $Port -WarningAction SilentlyContinue
if ($test.TcpTestSucceeded) {
    Write-Host "Redis is ready: localhost:$Port" -ForegroundColor Green
    Write-Host "Now restart the Spring Boot backend so it reconnects to Redis."
} else {
    Write-Host "Redis container started, but localhost:$Port is not reachable yet. Wait a few seconds and test again:" -ForegroundColor Yellow
    Write-Host "  Test-NetConnection localhost -Port $Port"
}
