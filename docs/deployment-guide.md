# Academic-Nexus v2.0.0 Deployment Guide

Updated: 2026-06-28

This guide covers the deployable v2.0.0 package, Docker Compose deployment, and the plain jar mode.

For the complete Docker-first reproducible deployment tutorial, see `docs/docker-deployment-guide.md`.

## Requirements

- Java 17
- Node.js 22, only needed when building the frontend locally
- Docker Desktop, recommended for MySQL, Redis, Nacos, and full Compose mode
- MySQL 8.4
- Redis 7.4
- Nacos 2.4.3

## Quick Docker Deployment

From the project root:

```powershell
copy .env.example .env
.\scripts\check-ports.ps1
.\scripts\docker-build.ps1
docker compose up -d
```

Services:

| Service | URL |
| --- | --- |
| Frontend dev server | `http://localhost:5174` |
| Main backend from host | `http://localhost:8088` |
| Main backend inside Compose network | `http://academic-main:8080` |
| AI service | `http://localhost:18090` |
| Nacos console | `http://localhost:18848/nacos` |
| MySQL | `localhost:13306` |
| Redis | `localhost:16379` |

Default Compose mode enables Nacos discovery and OpenFeign service-name calls between `academic-main` and `academic-ai-service`. Containers keep stable internal ports while host ports are configurable through `.env`.

Override host ports only when your machine ports are free:

```env
MAIN_HOST_PORT=18080
MYSQL_HOST_PORT=3306
REDIS_HOST_PORT=6379
FRONTEND_HOST_PORT=5173
```

If Docker Maven build fails with `bad_record_mac`, Central timeouts, or proxy problems, see `docs/docker-troubleshooting.md` and retry with:

```powershell
.\scripts\docker-build.ps1 -MavenMirror aliyun
```

Stop services:

```powershell
docker compose down
```

Keep data volumes if you want to preserve the database. Remove volumes only when you intentionally want a clean demo database:

```powershell
docker compose down -v
```

## Jar Package Deployment

Build the v2.0.0 release zip:

```powershell
.\scripts\build-release.ps1 -Version 2.0.0
```

Expected artifact:

```text
release/Academic-Nexus-2.0.0.zip
```

The zip contains:

- `academic-nexus-web.jar`
- `academic-nexus-ai-service.jar`
- `docker-compose.yml`
- `.env.example`
- `start-release.ps1`
- `start-release.bat`
- Documentation and verification notes

Start infrastructure:

```powershell
docker compose up -d nacos mysql redis
```

Create the database if needed:

```sql
CREATE DATABASE IF NOT EXISTS tianshiwebside
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Set environment variables:

```powershell
$env:SPRING_PROFILES_ACTIVE="demo"
$env:DB_URL="jdbc:mysql://localhost:13306/tianshiwebside?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="123123"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="16379"
$env:NACOS_ADDR="127.0.0.1:18848"
$env:NACOS_DISCOVERY_ENABLED="true"
$env:NACOS_REGISTER_ENABLED="true"
$env:AI_SERVICE_DISCOVERY_ENABLED="true"
$env:AI_SERVICE_NAME="academic-ai-service"
```

Start AI service:

```powershell
java -jar academic-nexus-ai-service.jar
```

Start main backend:

```powershell
java -jar academic-nexus-web.jar
```

## Ollama Mode

The AI service works without Ollama by returning deterministic fallback responses suitable for demos and tests.

To enable real local models:

```powershell
$env:OLLAMA_ENABLED="true"
$env:OLLAMA_BASE_URL="http://localhost:11434"
$env:OLLAMA_CHAT_MODEL="qwen3:8b"
$env:OLLAMA_SQL_MODEL="qwen2.5-coder:7b"
```

Pull models before starting the AI service:

```powershell
ollama pull qwen3:8b
ollama pull qwen2.5-coder:7b
```

## Verification Checklist

Run these before publishing a release:

```powershell
docker compose config
cd frontend
npm audit
npm run build
cd ..
.\mvnw.cmd test
cd ai-service
..\mvnw.cmd test
cd ..
.\scripts\health-check.ps1
.\scripts\check-ports.ps1
.\scripts\docker-build.ps1
.\scripts\build-release.ps1 -Version 2.0.0
```

Expected results for v2.0.0:

- Compose config resolves successfully.
- `npm audit` reports 0 vulnerabilities.
- Frontend production build passes.
- Main backend test suite passes, including v1.3.0 and v1.4.0 HTTP regression tests.
- AI service module builds successfully.
- Release zip is generated under `release/`.
- Health check Markdown and JSON reports are generated under `reports/`.

## Demo Accounts

The demo profile initializes administrator, teacher, and student accounts. For public distribution, do not publish private production credentials. Demo account values can be read from the local initializer or reset in the admin user page.

## Troubleshooting

If Flyway reports checksum drift on an old local database, use a clean demo database or run the documented Flyway repair flow after confirming the schema belongs to this project.

If Nacos is unavailable, set:

```powershell
$env:NACOS_DISCOVERY_ENABLED="false"
$env:NACOS_REGISTER_ENABLED="false"
$env:AI_SERVICE_DISCOVERY_ENABLED="false"
$env:AI_SERVICE_URL="http://localhost:8090"
```

The main system will call the AI service by fixed URL.
