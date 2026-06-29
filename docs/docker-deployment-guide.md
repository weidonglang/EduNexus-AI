# Academic-Nexus Docker Deployment Guide

Updated: 2026-06-29

This guide is the v2.0.0 Docker deployment entrypoint for Academic-Nexus.

v2.0.1 adds explicit SearXNG network and Ollama enablement notes. These notes are deployment-only changes; they do not require publishing a new release tag by Codex.

## 1. Scope

This guide applies to:

- Windows with Docker Desktop
- Linux with Docker Engine
- macOS with Docker Desktop
- A fresh machine cloning the project for the first time
- Local machines that already run MySQL, Redis, Nacos, Vite, or another Java service

## 2. Requirements

- Git
- Docker Desktop or Docker Engine
- Docker Compose v2
- At least 4 GB free memory, 8 GB recommended
- At least 5 GB free disk space
- Network access to Maven repositories and npm registry

Check commands:

```bash
git --version
docker --version
docker compose version
```

Windows PowerShell:

```powershell
git --version
docker --version
docker compose version
```

## 3. Get The Project

```bash
git clone https://github.com/weidonglang/Academic-Nexus.git
cd Academic-Nexus
```

Windows PowerShell:

```powershell
git clone https://github.com/weidonglang/Academic-Nexus.git
cd Academic-Nexus
```

## 4. Configure `.env`

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
copy .env.example .env
```

Important host ports:

```env
MAIN_HOST_PORT=8088
FRONTEND_HOST_PORT=5174
MYSQL_HOST_PORT=13306
REDIS_HOST_PORT=16379
NACOS_HOST_PORT=18848
NACOS_GRPC_HOST_PORT=19848
AI_SERVICE_HOST_PORT=18090
```

These values are host ports. Container-internal ports stay unchanged:

- `academic-main:8080`
- `academic-ai-service:8090`
- `mysql:3306`
- `redis:6379`
- `nacos:8848`

## 5. Default URLs

| Service | URL |
| --- | --- |
| Frontend | `http://localhost:5174` |
| Main service | `http://localhost:8088` |
| AI service | `http://localhost:18090/internal/ai/status` |
| Nacos | `http://localhost:18848/nacos` |
| MySQL | `localhost:13306` |
| Redis | `localhost:16379` |

## 6. Port Conflicts

Common conflicts:

- `3306`: local MySQL
- `6379`: local Redis
- `8848`: local Nacos
- `5173`: local Vite
- `8080`: another Java service

Windows:

```powershell
.\scripts\check-ports.ps1
netstat -ano | findstr ":3306"
netstat -ano | findstr ":5174"
```

Linux/macOS:

```bash
./scripts/check-ports.sh
lsof -i :3306
lsof -i :5174
```

Change only the left side host ports in `.env`:

```env
MYSQL_HOST_PORT=23306
REDIS_HOST_PORT=26379
FRONTEND_HOST_PORT=15174
```

Do not change container-internal ports in Compose.

## 7. Maven Download Failures

`bad_record_mac` usually means a Maven dependency download TLS, network, or proxy failure. It is not a business-code compile error.

Windows:

```powershell
.\scripts\docker-build.ps1
.\scripts\docker-build.ps1 -MavenMirror aliyun
.\scripts\docker-build.ps1 -NoCache
docker builder prune
docker compose build --no-cache academic-main
```

Linux/macOS:

```bash
./scripts/docker-build.sh
./scripts/docker-build.sh --maven-mirror aliyun
./scripts/docker-build.sh --no-cache
docker builder prune
docker compose build --no-cache academic-main
```

Default Maven Central:

```env
MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
```

Optional mirror for unstable networks:

```env
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
```

Do not force all users to use a regional mirror. Keep Maven Central as the default.

## 8. Build And Start

Windows:

```powershell
copy .env.example .env
.\scripts\check-ports.ps1
.\scripts\docker-build.ps1
docker compose up -d
```

Linux/macOS:

```bash
cp .env.example .env
./scripts/check-ports.sh
./scripts/docker-build.sh
docker compose up -d
```

Direct build/start is also supported:

```bash
docker compose up -d --build
```

## 9. Service Status

```bash
docker compose ps
docker compose logs -f academic-main
docker compose logs -f academic-ai-service
docker compose logs -f frontend
docker compose logs -f mysql
docker compose logs -f redis
docker compose logs -f nacos
```

Windows PowerShell uses the same commands.

## 10. First Startup Wait

First startup can take 1-3 minutes. The backend waits for MySQL, Redis, and Nacos health. If the frontend opens before the backend is ready, refresh the browser after services become healthy.

## 11. Verify Success

Open:

- `http://localhost:5174`
- `http://localhost:8088`
- `http://localhost:18090/internal/ai/status`
- `http://localhost:18848/nacos`

Commands:

```bash
curl http://localhost:8088
curl http://localhost:18090/internal/ai/status
```

Windows:

```powershell
Invoke-WebRequest http://localhost:8088
Invoke-WebRequest http://localhost:18090/internal/ai/status
```

## 12. SearXNG Real Search In Docker

Academic-Nexus backend containers access SearXNG from the container network. The Base URL configured in the admin page is from the `academic-main` container perspective, not from the browser perspective.

Browser access such as `http://localhost:8080` only proves the host can reach a service. It does not prove `academic-main` can reach `localhost:8080`, because `localhost` inside `academic-main` means the backend container itself.

Find the backend container network:

```powershell
docker inspect academic-main --format='{{range $k,$v := .NetworkSettings.Networks}}{{println $k}}{{end}}'
```

Example output:

```text
tianshiwebside_default
```

Connect SearXNG to the same Docker network:

```powershell
docker network connect --alias searxng tianshiwebside_default searxng-core
```

Verify from inside `academic-main`:

```powershell
docker exec academic-main wget -S -O- "http://searxng:8080/search?q=OpenAI&format=json"
```

Recommended admin page configuration:

```text
搜索提供方：SearXNG
Base URL：http://searxng:8080/search?q={query}&format=json
API Key 环境变量：留空
```

## 13. Enable Ollama For Docker AI Service

`academic-ai-service` being online does not mean Ollama is enabled. If `OLLAMA_ENABLED=false`, the UI showing local fallback mode is expected.

Create `.env` first:

```powershell
copy .env.example .env
```

Linux/macOS:

```bash
cp .env.example .env
```

Enable Ollama in `.env`:

```env
OLLAMA_ENABLED=true
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_CHAT_MODEL=qwen3:8b
OLLAMA_SQL_MODEL=qwen2.5-coder:7b
```

After changing `.env`, recreate the related containers:

```powershell
docker compose up -d --force-recreate academic-ai-service academic-main
```

Check environment variables inside the container:

```powershell
docker exec academic-ai-service printenv | findstr OLLAMA
```

Verify Ollama on the host:

```powershell
curl.exe http://localhost:11434/api/tags
```

Verify Ollama from inside the AI container:

```powershell
docker exec academic-ai-service wget -S -O- "http://host.docker.internal:11434/api/tags"
```

Check and pull models:

```powershell
ollama list
ollama pull qwen3:8b
ollama pull qwen2.5-coder:7b
```

## 14. FAQ

Q1: MySQL port is occupied.

```env
MYSQL_HOST_PORT=13306
```

Q2: Redis port is occupied.

```env
REDIS_HOST_PORT=16379
```

Q3: Frontend port is occupied.

```env
FRONTEND_HOST_PORT=5174
```

Q4: Maven download failed.

```powershell
.\scripts\docker-build.ps1 -MavenMirror aliyun
```

Q5: Nacos starts slowly.

```bash
docker compose logs -f nacos
```

Wait until Nacos is healthy.

Q6: Frontend opens but APIs fail.

```bash
docker compose ps
docker compose logs -f academic-main
```

Q7: AI service looks offline.

```bash
docker compose logs -f academic-ai-service
```

Default demo mode is valid with:

```env
OLLAMA_ENABLED=false
AI_SERVICE_URL=http://academic-ai-service:8090
```

## 15. Stop And Clean

Stop and keep data:

```bash
docker compose down
```

Delete volumes:

```bash
docker compose down -v
```

Clean build cache:

```bash
docker builder prune
```

## 16. Upgrade

```bash
git pull
docker compose build
docker compose up -d
```

Watch Flyway migration:

```bash
docker compose logs -f academic-main
```

## 17. Acceptance Checklist

- [ ] `git clone` succeeded
- [ ] `.env` was created from `.env.example`
- [ ] port check passed
- [ ] Docker build passed
- [ ] `docker compose up -d` succeeded
- [ ] `docker compose ps` shows services healthy or running
- [ ] frontend is reachable
- [ ] main service is reachable
- [ ] AI service status is reachable
- [ ] MySQL has no host port conflict
- [ ] Redis has no host port conflict
- [ ] Nacos is reachable
