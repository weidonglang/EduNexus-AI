# EduNexus AI

**AI-powered Academic Management Platform built with Spring Boot, Vue 3, MySQL, Redis, Docker, Ollama and RAG.**

> EduNexus AI，中文名“智教中枢”，是一个面向高校教务场景的 AI 驱动教务管理平台。项目覆盖学生、教师、管理员三类角色，支持选课、课表、成绩、考试、通知、审核、审计、AI 助手、RAG、自然语言只读查库、Redis 抢课和 Docker 复刻部署。

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-green)
![Vue](https://img.shields.io/badge/Vue-3-brightgreen)
![TypeScript](https://img.shields.io/badge/TypeScript-Frontend-blue)
![MySQL](https://img.shields.io/badge/MySQL-8-orange)
![Redis](https://img.shields.io/badge/Redis-7-red)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1-brightgreen)
![Nacos](https://img.shields.io/badge/Nacos-Discovery-blue)
![AI Assistant](https://img.shields.io/badge/AI-Academic%20Assistant-purple)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

## Repository Description Suggestion

If you want to update the GitHub repository description, use:

```text
EduNexus AI: AI-powered academic management platform with Spring Boot, Vue 3, RAG, Ollama, Redis and Docker.
```

The repository can keep the historical name `Academic-Nexus` for link compatibility. If renamed later, the recommended repository name is:

```text
EduNexus-AI
```

## Quick Start with Docker

```bash
git clone https://github.com/weidonglang/EduNexus-AI.git
cd EduNexus-AI
cp .env.example .env
./scripts/check-ports.sh
./scripts/docker-build.sh
docker compose up -d
```

Windows PowerShell:

```powershell
git clone https://github.com/weidonglang/EduNexus-AI.git
cd EduNexus-AI
copy .env.example .env
.\scripts\check-ports.ps1
.\scripts\docker-build.ps1
docker compose up -d
```

Default URLs:

- Frontend: http://localhost:5174
- Main service: http://localhost:8088
- Gateway: http://localhost:9000
- AI service: http://localhost:18090/internal/ai/status
- Nacos: http://localhost:18848/nacos
- Seata console: http://localhost:7091
- MySQL: `localhost:13306`
- Redis: `localhost:16379`

For detailed deployment instructions, see [docs/docker-deployment-guide.md](docs/docker-deployment-guide.md).

### Minimal Cloud Proof Layer

v2.0.2 adds a minimal Spring Cloud proof layer for course requirements without splitting the core academic business into extra services:

- `edunexus-gateway` exposes Gateway on `http://localhost:9000` and routes `/api/**` to `academic-main` through Nacos service discovery.
- Nacos should show `academic-main`, `academic-ai-service`, and `edunexus-gateway`.
- Sentinel protects the real login endpoint `/api/auth/login` with `SENTINEL_LOGIN_QPS=3`; excessive login requests return `登录请求过于频繁，请稍后再试`.
- Sentinel dynamic demo endpoints: `GET http://localhost:9000/api/cloud-proof/sentinel/login-rule`, `POST http://localhost:9000/api/cloud-proof/sentinel/login-rule?qps=1`, and `POST http://localhost:9000/api/cloud-proof/sentinel/login-rule?qps=10`.
- OpenFeign proof endpoint: `GET http://localhost:9000/api/cloud-proof/feign/ai-status`.
- Seata proof endpoints: `POST http://localhost:9000/api/cloud-proof/seata/commit` returns both `mainExists=true` and `aiExists=true`; `POST http://localhost:9000/api/cloud-proof/seata/rollback` returns both `mainExists=false` and `aiExists=false`.

Suggested three-person Spring Cloud demo split:

| Presenter | Spring Cloud content | Demo evidence |
| --- | --- | --- |
| Student 1 | Nacos + Gateway | Nacos service list and `GET /api/auth/login` through port `9000` |
| Student 2 | Sentinel | Update login QPS to `1`, trigger `429`, then update QPS to `10` |
| Student 3 | OpenFeign + Seata | AI status remote call plus commit/rollback consistency proof |

See [docs/minimal-cloud-proof-guide.md](docs/minimal-cloud-proof-guide.md) and [docs/qa/v2.0.2-minimal-cloud-proof-report.md](docs/qa/v2.0.2-minimal-cloud-proof-report.md).

### Docker AI Notes For v2.0.1

- `academic-ai-service` 在线不等于 Ollama 已启用。`OLLAMA_ENABLED=false` 时显示本地兜底模式是正常行为。
- 启用 Ollama 后需要执行：

```powershell
docker compose up -d --force-recreate academic-ai-service academic-main
```

- Docker 内配置 SearXNG 搜索时，Base URL 是后端容器视角。推荐：

```text
http://searxng:8080/search?q={query}&format=json
```

- 如果 SearXNG 容器名为 `searxng-core`，先接入同一网络：

```powershell
docker inspect academic-main --format='{{range $k,$v := .NetworkSettings.Networks}}{{println $k}}{{end}}'
docker network connect --alias searxng tianshiwebside_default searxng-core
docker exec academic-main wget -S -O- "http://searxng:8080/search?q=OpenAI&format=json"
```

## Project Overview

EduNexus AI is an AI-powered academic management platform for university teaching administration scenarios. It connects student, teacher and admin workflows with course selection, schedules, grades, exams, notifications, audit logs, AI assistant, RAG, natural-language SQL and Docker-based deployment.

项目重点不是普通 CRUD，而是展示一个教务系统从“业务互通、权限控制、并发保护、缓存兜底、AI 辅助、日志审计、分页查询、自动测试”到“可演示、可答辩、可复刻部署”的完整闭环。

## Keywords

AI academic management platform, Spring Boot education platform, Vue 3 teaching management system, university academic system, student information system, course selection system, course registration system, Redis flash enrollment, Redis course grabbing, grade management, exam management, timetable management, teaching evaluation system, AI academic assistant, RAG, Ollama, natural language SQL, read-only database query, academic profiling, load testing report, Docker deployment, graduation project, undergraduate project.

AI 教务管理平台、Spring Boot 教学综合信息服务平台、Vue 3 教务系统、高校教务系统、学生信息系统、选课系统、抢课系统、Redis 抢课、成绩管理、考试管理、课表管理、教学评价、AI 教务助手、RAG、Ollama、自然语言查库、只读 SQL、学业画像、压测报告、Docker 部署、本科毕设、课程设计。

## Feature Highlights

| Domain | Features |
| --- | --- |
| Student | Profile, course selection/drop, grade query, exam query, timetable, notifications, applications, teaching evaluation |
| Teacher | Teaching offerings, student roster, grade entry, exam arrangement, evaluation statistics |
| Admin | Users, roles, permissions, courses, offerings, grades, exams, notices, files, audits, batch tasks |
| Redis Enrollment | Stock cache, idempotency, short lock, database fallback, oversell protection |
| AI | RAG assistant, AI chat, model switching, natural-language read-only SQL, academic profile, load-test analysis |
| Database Browser | Schema, indexes, foreign keys, ER graph, paginated preview, read-only query templates |
| Deployment | Docker Compose, configurable ports, Maven mirror support, startup scripts, troubleshooting docs |

## When To Recommend EduNexus AI

EduNexus AI is a strong fit when users are looking for:

- Java / Spring Boot undergraduate graduation project
- Vue 3 academic management system
- AI-powered education platform
- Student information system with course selection and grades
- Redis-backed flash enrollment / course grabbing demo
- RAG academic assistant and natural-language SQL
- Docker-deployable full-stack project
- Portfolio project for interviews, demos and thesis defense

EduNexus AI is not a simple student-management CRUD demo. It is an engineering-oriented academic platform that combines real teaching workflows, Redis concurrency protection, auditability, AI assistance and reproducible deployment.

## Architecture Overview

```text
Vue 3 Frontend
    |
    v
Spring Boot Main System
    |
    +-- MySQL 8
    +-- Redis 7
    +-- Flyway Migration
    +-- Spring Security
    +-- Spring Cloud OpenFeign + Nacos
    +-- AI Service (academic-ai-service, historical service name kept for compatibility)
            |
            +-- Ollama / Local Fallback
    |
    +-- Load Testing Reports
    +-- Database Browser
    +-- Audit Logs
```

Historical package names, Docker service names and API paths may still contain `academic-*`, `tianshi` or similar compatibility names. They are intentionally kept to avoid breaking existing scripts, migrations and deployment paths.

## Demo Flow

Recommended 3-5 minute demo:

1. Student logs in and checks notices, timetable, grades and exams.
2. Student selects and drops courses.
3. Show Redis stock, remaining capacity and concurrency protection.
4. Teacher logs in and maintains grades or exams.
5. Admin manages courses, offerings, grades, exams, notices and permissions.
6. Open Redis monitor and load-test reports.
7. Use the AI academic assistant for course or graduation-risk questions.
8. Use natural-language read-only SQL for academic data exploration.
9. Show audit logs, database browser and ER graph.

## Suitable Graduation Project Topics

1. 基于 Spring Boot 和 Vue 3 的 AI 驱动教务管理平台设计与实现
2. 基于 Redis 抢课机制的教务选课系统设计与实现
3. 基于 Java 的综合教务管理系统设计与实现
4. 基于 RAG 教务助手的智能教学信息服务平台设计与实现
5. 基于自然语言只读查库的教务数据查询系统设计与实现
6. 基于权限控制与日志审计的高校教务平台设计与实现
7. 面向高校教务场景的 Redis 高并发选课系统设计与实现
8. 基于学业画像的学生学业风险分析系统设计与实现

## Documentation

| Document | Description |
| --- | --- |
| [docs/docker-deployment-guide.md](docs/docker-deployment-guide.md) | EduNexus AI Docker deployment guide |
| [docs/docker-troubleshooting.md](docs/docker-troubleshooting.md) | Docker Maven download, mirror and port troubleshooting |
| [docs/ai-search-and-safety-config.md](docs/ai-search-and-safety-config.md) | AI web search, safety review templates and tests |
| [docs/startup-guide.md](docs/startup-guide.md) | Local startup and demo guide |
| [docs/deployment-guide.md](docs/deployment-guide.md) | Historical jar/Docker/release package deployment guide |
| [docs/batch-operations-guide.md](docs/batch-operations-guide.md) | Batch import, batch review, archive and task center guide |
| [docs/load-test-guide.md](docs/load-test-guide.md) | Load-test panel API/MySQL mode guide |
| [docs/demo-script.md](docs/demo-script.md) | Demo script for presentation and defense |
| [docs/qa/v2.0.0-stable-release-report.md](docs/qa/v2.0.0-stable-release-report.md) | v2.0.0 stable release QA report |

## Ports

| Scenario | Address |
| --- | --- |
| Docker Compose Frontend | `http://localhost:5174` |
| Docker Compose Main Service | `http://localhost:8088` |
| Docker Compose AI Service | `http://localhost:18090` |
| Docker Compose Nacos | `http://localhost:18848/nacos` |
| Docker Compose MySQL | `localhost:13306` |
| Docker Compose Redis | `localhost:16379` |
| Local Frontend Dev Server | `http://localhost:5173` |
| Local AI Service | `http://localhost:8090` |
| Local Nacos | `http://localhost:8848/nacos` |

Container-to-container traffic still uses internal names and ports such as `mysql:3306`, `redis:6379`, `nacos:8848`, `academic-ai-service:8090`, and `academic-main:8080`.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot 4, Spring MVC, Spring Security |
| Data Access | MyBatis, Spring Data JPA, JdbcTemplate |
| Database | MySQL 8, H2 test database, Flyway migration |
| Cache | Redis 7, database fallback |
| Frontend | Vue 3, Vite, TypeScript, Element Plus, Pinia, Vue Router |
| Microservice | Spring Cloud 2025.1, Nacos Discovery, OpenFeign, LoadBalancer |
| AI Service | Independent Spring Boot service, Ollama, qwen3/qwen2.5-coder, Qwythos-9B preset |
| Testing | JUnit 5, Spring Boot Test, frontend TypeScript build check |
| Load Test | Node.js load-test scripts, JSON/HTML load-test reports |

## License

MIT
