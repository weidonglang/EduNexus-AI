# 教学综合信息服务平台复刻项目

> 本项目是课程设计/毕业设计场景下的教学综合信息服务平台复刻项目，仅用于学习、演示和课程答辩。

## 项目简介

本项目围绕高校教务系统的核心流程实现了一套前后端分离应用，覆盖学生、教师、管理员三类角色。系统包含选课、成绩、考试、课表、学籍异动、报名审核、通知公告、教学评价、数据库只读浏览、Redis 抢课、压测报告和 AI 辅助教务等模块。

项目重点不是单纯堆功能，而是展示一个教务系统从“业务互通、权限控制、并发保护、缓存兜底、AI 辅助、日志审计、分页查询、自动测试”到“可演示、可答辩”的完整闭环。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 4、Spring MVC、Spring Security |
| 数据访问 | MyBatis、Spring Data JPA、JdbcTemplate |
| 数据库 | MySQL 8、H2 测试库、Flyway 数据库迁移 |
| 缓存 | Redis 7，可降级到数据库兜底 |
| 前端 | Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router |
| AI 服务 | 独立 Spring Boot 微服务、Ollama、qwen3/qwen2.5-coder |
| 测试 | JUnit 5、Spring Boot Test、前端 TypeScript 构建检查 |
| 压测 | Node.js 压测脚本、压测报告 JSON/HTML |

## 核心功能

### 学生端

- 首页公告、站内通知、未读提醒。
- 个人信息、联系方式维护、学籍信息查看。
- 自主选课、Redis 抢课、退课、已选课程查询。
- 成绩查询、考试安排查询、个人课表、班级课表、空闲教室。
- 学籍异动申请、附件上传、审核结果查看。
- 微专业报名、重修报名、学分替代、成绩加分、专业方向确认等报名申请。
- 教学评价、教学信息反馈、毕业设计成绩、学业预警、毕业审核、培养方案查询。
- 所有主要查询结果均支持分页展示。

### 教师端

- 查看本人任课教学班。
- 录入和维护本人课程成绩。
- 维护本人课程考试安排。
- 查看教学评价统计结果。
- 与管理员新增课程、学生选课、学生评价等流程互通。

### 管理端

- 用户、角色、菜单权限管理。
- 课程、教学班、容量、选课时间窗口维护。
- 成绩管理、成绩导入导出、成绩发布、锁定。
- 考试安排、考场、座位、监考信息维护。
- 学籍异动审核、报名申请审核。
- 通知公告发布、已读未读统计。
- 教学评价统计、文件管理、操作审计。
- Redis 状态监控、抢课库存预热、压测报告查看和 AI 解读。
- 数据库只读浏览、表结构、索引、外键、ER 图、数据分页预览。

### AI 功能

- RAG 智能教务助手：基于规则、公告、教学计划、学生学业概况回答问题。
- 证据来源展示：回答下方展示命中来源、标题、内容和相关度。
- 无法回答机制：知识不足、权限外数据、非教务问题会拒答或兜底。
- 通用 AI 聊天：用于系统介绍、答辩准备、文本润色和普通问答。
- 自然语言只读查库：管理员输入业务问题，AI 生成 `SELECT` 草稿，主系统做只读和敏感字段校验后执行。
- AI 服务状态检测：展示 ai-service、Ollama、模型、模式、耗时和错误信息。
- AI 调用日志：记录 RAG、聊天、SQL、学业画像、压测解读等调用。
- 学业画像：展示已修学分、剩余学分、挂科课程、毕业风险和 AI 建议。
- AI 压测报告解读：对抢课压测数据生成结论、风险判断和优化建议。

## Redis 抢课设计

Redis 是本系统的抢课加速层，同时支持不可用时自动降级到数据库模式。

| 模块 | Redis Key | 作用 |
| --- | --- | --- |
| 库存缓存 | `selection:offering:{offeringId}:remaining` | 保存教学班剩余名额，抢课先扣 Redis 库存 |
| 请求幂等 | `selection:request:{requestId}` | 防止同一次请求重复提交 |
| 短锁保护 | `selection:grab:lock:{offeringId}:{username}` | 限制同一学生短时间重复抢同一教学班 |

并发保护策略：

- 优先使用 Redis 扣减库存。
- 数据库层面校验容量，避免超卖。
- 写库失败时回滚 Redis 库存。
- Redis 不可用时自动走数据库兜底。
- 压测报告会记录 Redis 状态、成功数、满员数、失败样例和延迟指标。

## 项目结构

```text
.
├─ src/main/java/weidonglang/tianshiwebside
│  ├─ academic          成绩、考试、空教室
│  ├─ admin             系统监控、数据库浏览
│  ├─ ai                主系统 AI 接口转发与日志
│  ├─ audit             操作审计
│  ├─ auth              登录认证
│  ├─ course            课程、教学班、选课、抢课
│  ├─ dashboard         首页数据
│  ├─ evaluation        教学评价
│  ├─ file              附件和文件管理
│  ├─ information       信息查询中心
│  ├─ notice            通知公告
│  ├─ permission        菜单和角色权限
│  ├─ schedule          课表
│  ├─ student           学生资料、学籍异动、报名申请
│  ├─ teacher           教师端业务
│  └─ user              用户管理
├─ src/main/resources
│  └─ db/migration      Flyway 数据库迁移脚本
├─ frontend             Vue 前端项目
├─ ai-service           独立 AI 微服务
├─ scripts              抢课压测脚本
├─ reports              压测报告输出目录
├─ docs                 项目文档
└─ start-all.bat        一键启动脚本
```

## 环境要求

- JDK 17
- Node.js 20+
- MySQL 8
- Docker Desktop，可选，用于启动 Redis 容器
- Redis 7，可选；没有 Redis 时系统会数据库兜底
- Ollama，可选；没有 Ollama 时 AI 功能可走本地兜底

## 快速启动

### 1. 创建数据库

```sql
create database if not exists tianshiwebside
  default character set utf8mb4
  collate utf8mb4_unicode_ci;
```

默认数据库配置在 `src/main/resources/application.properties`：

```properties
DB_URL=jdbc:mysql://localhost:3306/tianshiwebside...
DB_USERNAME=root
DB_PASSWORD=123123
```

也可以参考 `.env.example` 设置环境变量覆盖默认值。

### 2. 一键启动

Windows 下推荐直接运行：

```powershell
.\start-all.bat
```

该脚本会尝试启动：

- Redis：`localhost:6379`
- AI 服务：`http://localhost:8090`
- 后端主系统：`http://localhost:8080`
- 前端 Vite：`http://localhost:5173`

浏览器访问：

```text
http://localhost:5173
```

### 3. 手动启动

后端主系统：

```powershell
$env:SPRING_PROFILES_ACTIVE="demo"
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

AI 服务：

```powershell
cd ai-service
$env:OLLAMA_ENABLED="true"
$env:OLLAMA_CHAT_MODEL="qwen3:8b"
$env:OLLAMA_SQL_MODEL="qwen2.5-coder:7b"
..\mvnw.cmd spring-boot:run
```

Redis：

```powershell
.\start-redis.ps1
```

如果 Docker 无法拉取 Redis 镜像，可以先手动执行：

```powershell
docker pull redis:7-alpine
```

## Ollama 配置

如果需要使用真实模型能力，先安装并启动 Ollama，然后拉取模型：

```powershell
ollama pull qwen3:8b
ollama pull qwen2.5-coder:7b
```

常用环境变量：

```powershell
$env:OLLAMA_ENABLED="true"
$env:OLLAMA_BASE_URL="http://localhost:11434"
$env:OLLAMA_CHAT_MODEL="qwen3:8b"
$env:OLLAMA_SQL_MODEL="qwen2.5-coder:7b"
```

如果不想使用 Ollama：

```powershell
$env:OLLAMA_ENABLED="false"
.\start-all.bat
```

系统会显示本地兜底模式，基础回答仍可用。

## 演示账号

`demo` 或 `dev` 环境启动时会自动初始化演示数据。

| 账号 | 密码 | 说明 |
| --- | --- | --- |
| `23111141` | `123456` | 全角色演示账号，具备学生、教师、管理员权限 |
| `t001` | `123456` | 教师演示账号 |
| `230001` 起 | `123456` | 批量生成的学生演示账号 |

公开部署前请修改默认密码，不要接入真实个人信息。

## 常用页面

| 功能 | 地址 |
| --- | --- |
| 前端首页 | `http://localhost:5173` |
| 后端主服务 | `http://localhost:8080` |
| AI 服务 | `http://localhost:8090` |
| 智能教务助手 | `/ai/assistant` |
| AI 聊天 | `/ai/chat` |
| 学业画像 | `/ai/academic-profile` |
| 自然语言只读查库 | `/admin/ai/natural-sql` |
| AI 调用日志 | `/admin/ai/call-logs` |
| Redis 状态监控 | `/admin/redis-monitor` |
| 压测报告 | `/admin/load-test-reports` |
| 数据库只读浏览 | `/admin/database-browser` |

## 压测

抢课压测脚本位于：

```text
scripts/course-grab-load-test.js
```

示例：

```powershell
$env:LOAD_USERS="1000"
$env:REQUESTS="1000"
$env:CONCURRENCY="100"
$env:SMART_MODE="random"
node .\scripts\course-grab-load-test.js
```

报告输出到：

```text
reports/
```

管理端可以在“压测历史报告”中查看 JSON/HTML 报告，并调用 AI 生成压测解读。

## 构建与测试

前端类型检查和构建：

```powershell
cd frontend
npm run build
```

后端完整测试：

```powershell
.\mvnw.cmd test
```

后端打包：

```powershell
.\mvnw.cmd clean package -DskipTests
```

## 安全说明

- 数据库浏览模块只提供白名单和只读能力，不提供任意写 SQL。
- 自然语言查库只允许单条 `SELECT`，并拦截敏感字段、多语句和写操作。
- Redis 只是加速层，不可用时系统会数据库兜底。
- 本项目包含演示账号和演示数据，不建议直接用于生产环境。
- 不要提交真实 `.env`、数据库密码、日志、上传文件或压测报告。

## 许可证

本项目采用 MIT License，详见 [LICENSE](LICENSE)。
