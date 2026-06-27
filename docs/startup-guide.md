# 项目启动说明

本文档说明如何启动教学综合信息服务平台的后端、前端、Redis 和压测工具。

## 推荐方式：MySQL + demo profile

适合正式演示。使用 MySQL 数据库，并自动初始化学院、专业、班级、学生、教师、管理员、课程、成绩、考试、公告等演示数据。

### 1. 准备 MySQL 数据库

先确认 MySQL 已启动，然后执行：

```sql
CREATE DATABASE IF NOT EXISTS tianshiwebside
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

本机数据库账号、密码请使用自己的 MySQL 配置。不要把个人数据库密码写入文档或提交到代码仓库。

可以在 IDEA 环境变量里覆盖：

```text
DB_USERNAME=你的MySQL用户名
DB_PASSWORD=你的MySQL密码
```

### 2. IDEA 启动后端

打开 `Run/Debug Configurations`，选择 `TianshiwebsideApplication`。

```text
Active profiles: demo
Program arguments: --server.port=8080
```

启动成功时会看到：

```text
Tomcat started on port 8080
Started TianshiwebsideApplication
```

第一次启动时，Flyway 会自动建表，`DataInitializer` 会自动灌入演示数据。

### 3. 启动前端

```powershell
cd E:\javacode\tianshiwebside\frontend
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

前端开发配置在 `frontend/.env.development`：

```env
VITE_DEV_PORT=5173
VITE_API_PROXY_TARGET=http://localhost:8080
```

## 登录账号

演示账号由系统初始化数据提供。为了避免文档中明文展示账号和密码，请向项目负责人获取演示账号。

如果需要新增、重置或禁用账号，请登录管理员后台的“用户与角色”页面维护。

## H2 备用方式

适合不想安装 MySQL 时临时演示。数据会在每次后端重启后重新生成。

IDEA 配置：

```text
Active profiles: dev
Program arguments: --server.port=8080
```

## Redis 单机模式

默认启动使用单机 Redis：

```text
localhost:6379
```

检查方式：

```powershell
Test-NetConnection localhost -Port 6379
```

如果显示：

```text
TcpTestSucceeded : True
```

说明 Redis 可以被后端连接。

## Redis 集群模式

如果需要切换到 Redis Cluster，启用额外 profile：

```text
Active profiles: demo,redis-cluster
```

并配置集群节点环境变量：

```powershell
$env:REDIS_CLUSTER_NODES="192.168.1.10:7000,192.168.1.10:7001,192.168.1.10:7002"
```

当前答辩演示建议使用单机 Redis，链路更简单，也更容易讲清楚。

## Spring Cloud + Nacos

本项目从 1.1 版本开始接入 Spring Cloud，v1.2 补齐 Docker Compose、Nacos 注册发现和 OpenFeign 验证文档：

- 主系统服务名：`academic-main`，默认端口 `8080`
- AI 服务名：`academic-ai-service`，默认端口 `8090`
- 注册中心：Nacos，默认地址 `127.0.0.1:8848`
- 服务调用：主系统通过 OpenFeign 按服务名调用 `academic-ai-service`

本地准备基础设施：

```powershell
docker compose up -d nacos mysql redis
```

如果需要让两个 Java 服务注册到 Nacos，并让主系统走服务发现调用 AI 服务，设置：

```powershell
$env:NACOS_DISCOVERY_ENABLED="true"
$env:NACOS_REGISTER_ENABLED="true"
$env:AI_SERVICE_DISCOVERY_ENABLED="true"
$env:NACOS_ADDR="127.0.0.1:8848"
```

启动后访问 Nacos 控制台：

```text
http://localhost:8848/nacos
```

在服务列表里应能看到 `academic-main` 和 `academic-ai-service`。如果不想启动 Nacos，可以保持默认配置，主系统会继续通过 `AI_SERVICE_URL=http://localhost:8090` 调用 AI 服务。

## 静态演示页

```powershell
.\start-preview.ps1
```

访问：

```text
http://localhost:8091/preview.html
```

如需临时改端口，可以先设置 `PREVIEW_PORT`：

```powershell
$env:PREVIEW_PORT="8092"
.\start-preview.ps1
```

## 压测面板

```powershell
.\start-load-panel.bat
```

压测面板不会再预填账号密码。运行压测前需要手动填写管理员账号、管理员密码和压测学生密码。

常用流程：

1. 刷新课程。
2. 选择教学班。
3. 填写压测所需账号信息。
4. 选择是否开启 Redis 缓存。
5. 选择 `random` 模式。
6. 开始压测。
7. 查看 `reports/` 目录下生成的 HTML 报告。

## 一键脚本

开发机本地启动后端和前端：

```powershell
.\start-project.ps1
```

完整演示启动 Redis、AI 服务、后端和前端：

```powershell
.\start-all.bat
```

数据库账号密码请通过环境变量覆盖，例如 `DB_USERNAME` 和 `DB_PASSWORD`。脚本不会在控制台直接输出演示账号密码。

## Release 打包

生成可分发的 jar 和 zip：

```powershell
.\scripts\build-release.ps1 -Version 1.2
```

打包产物位于：

```text
release/Academic-Nexus-1.2.zip
```

压缩包内包含主系统 `academic-nexus-web.jar`、AI 服务 `academic-nexus-ai-service.jar`、`docker-compose.yml`、`.env.example`、`start-release.ps1` 和 `start-release.bat`。部署机器需要 Java 17；MySQL、Redis、Nacos 可以用压缩包里的 Docker Compose 启动。如需真实 Ollama 模型，把 `.env` 中的 `OLLAMA_ENABLED` 改为 `true` 并确认模型已经拉取。

## v1.2 Docker Compose 全栈启动

如果 Docker Desktop 已启动，可以直接运行：

```powershell
docker compose up -d --build
```

该模式会启动 Nacos、MySQL、Redis、AI 服务、主系统和前端。主系统通过 OpenFeign + Nacos 服务名 `academic-ai-service` 调用 AI 服务。访问地址：

```text
前端：http://localhost:5173
后端：http://localhost:8080
Nacos：http://localhost:8848/nacos
```

发布前推荐执行：

```powershell
docker compose config
cd frontend
npm audit
npm run build
cd ..
.\mvnw.cmd test
cd ai-service
..\mvnw.cmd test
```

详细部署说明见 `docs/deployment-guide.md`，Spring Cloud 验证说明见 `docs/spring-cloud-verification.md`。
