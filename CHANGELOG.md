# Changelog

## v2.0.2 - Minimal Cloud Proof Layer - Unreleased

### Added

- Added standalone `gateway-service` with Spring Cloud Gateway routing `/api/**` to `academic-main` through Nacos.
- Added Docker Compose services and ports for `edunexus-gateway` and Seata Server.
- Added Sentinel protection on the real `/api/auth/login` endpoint with configurable QPS and a Chinese flow-control message.
- Added OpenFeign proof endpoint `/api/cloud-proof/feign/ai-status`.
- Added Seata proof endpoints `/api/cloud-proof/seata/commit` and `/api/cloud-proof/seata/rollback` using demo tables only.
- Added cloud proof Flyway migration, Docker env wiring, startup/deployment docs, and QA evidence guide.

### Boundaries

- Core academic business remains in the main service.
- Course selection, grades, exams, applications, and notices are not forced into Seata transactions.

## v2.0.1 - AI, Docker and Frontend UX Fixes - Unreleased

### Branding

- Renamed the project display name from Academic-Nexus to **EduNexus AI**.
- Added English positioning: **AI-powered Academic Management Platform**.
- Updated README, frontend title, login page, shell footer, Docker docs, AI search docs, AI prompts and release notes draft to use EduNexus AI.
- Kept internal Java package names, API paths, historical Docker service names and repository path unchanged for compatibility.

### Fixed

- Clarified SearXNG real search Docker network configuration.
- Clarified Ollama enablement for Docker ai-service deployment.
- Fixed web-search grounding so local model answers use returned references.
- Fixed AI model registry default/deleted/enabled state constraints.
- Fixed top global search interaction.
- Fixed dashboard quick-app settings interaction.
- Fixed AI Chat Markdown rendering for numbered lists, bold text, inline code, links and code blocks.

### Improved

- Improved AI Chat frontend state, loading, retry, copy, error and reference feedback.
- Added AI thinking mode switch: Auto / On / Off.
- Added `thinkingMode` propagation from frontend to main service and ai-service.
- Added branch cleanup report.
- Added local and Docker run verification.
- Added v2.0.1 QA report.

## v2.0.0 - Stable Complete Edition - 2026-06-28

### Final stabilization

- Fixed Redis stock rebuild after course drop by rebuilding `selection:offering:{offeringId}:remaining` from database capacity and selected count.
- Added final three-role visibility assertions for batch user import and batch course offering import.
- Added AI model safe delete workflow with soft deletion, default-model protection, enabled-model protection, audit records, and regression tests.
- Added web search configuration templates, safety review templates, and observable configuration test results.
- Fixed dashboard statistics to be role-scoped for students, teachers, and admins.
- Added complete Docker deployment guide and v2.0.0 stable release QA report.

### Deployment

- Clarified Docker host ports versus container ports.
- Documented Maven mirror troubleshooting for Docker builds, including `bad_record_mac` network failures.
- Added reproducible Docker startup checklist for Windows, Linux, and macOS.

### Stable closure

- Completed #76-#104.
- Completed #106-#108.
- Promoted the project to v2.0.0 Stable Complete Edition.

## v1.4.1-final-closure - 2026-06-28

- Added real CSV preview/commit flows for batch user import, including student profile/class data, batch tasks, audit records, and regression tests.
- Added real CSV preview/commit flows for course and offering import, including teacher/schedule/window validation, batch tasks, audit records, and Redis stock prewarm.
- Added batch review APIs and UI for status-change and registration applications with partial-success details, skipped processed rows, notifications, cache eviction, batch tasks, and audit.
- Added targeted notice preview/publish for role, grade, major, class, and offering scopes with zero-recipient protection and targeted audit.
- Added teacher read-only awareness APIs for homeroom class applications and course-related registration summaries.
- Enhanced admin grade updates with old/new audit details, high-risk locked-grade audit, traceability, and student notifications.
- Hardened Docker reproducible builds with BuildKit cache, Maven `dependency:go-offline`, mirror settings, and build scripts.
- Made Docker host ports configurable with non-conflicting defaults and fixed a Nacos startup race by waiting for Nacos health and disabling discovery fail-fast in Compose.
- Added final closure QA reports for batch import, three-role flow, notifications, grade audit, Docker build, and Docker ports.

## v1.4.1-open-issues-closure - 2026-06-27

- Propagated the selected AI chat model from frontend session calls through the main system to ai-service, and recorded selected model, actual model, fallback flag, and fallback reason in AI call logs.
- Hardened sensitive-word and moderation-log admin pages against partial backend failures.
- Fixed `course_grab_panel.py` backend API parsing for both array and paged response shapes, with term filtering and diagnostics.
- Added authenticated blob download/preview flows for status-change attachments, database CSV export, data archive CSV export, and batch task reports.
- Added refresh-token rotation, logout revocation, disabled-user token rejection, and token revocation when admins disable or lock users.
- Added upload-after-review protection and audit records for status-change attachment upload, download, preview, and delete.
- Added configurable current-term resolution and removed hardcoded course-selection term assumptions.
- Added schedule parsing validation so abnormal schedule text is surfaced without breaking the timetable grid.
- Tightened grade locking so locked grades cannot be modified by changing the request payload, and added grade-point range validation.
- Added student notifications for teacher/admin exam create, update, and delete flows.
- Enhanced system health with runtime profile/port, Nacos discovery config, demo-data completeness, and release-package checks.
- Added Redis stock prewarm audit records and batch-task CSV report download.
- Expanded database-browser masking and isolated partial schema/index/foreign-key loading failures.
- Completed frontend fallback menu entries for system health, data dictionary, sensitive words, consistency checks, and AI model administration.

## v1.4.0-final-polish - 2026-06-27

- Changed Docker demo backend host port to `8088` by default while keeping the container and local IDEA port on `8080`; `MAIN_HOST_PORT` can override it.
- Enhanced AI call logs with keyword, user, function, success, level, traceId, service mode, and time-range filtering.
- Added persistent AI chat sessions, model switching, history loading, rename/delete, and model-aware call-log records.
- Added administrator status-change attachment list, preview, and download endpoints with Chinese file type labels and role-boundary regression tests.
- Improved `scripts/course_grab_panel.py` with backend API course refresh by default and configurable MySQL direct mode.
- Added safe batch task center, data archive/cleanup records, database query templates, CSV export, and audit records.
- Added richer menu wiring for batch tasks, data archive, database templates, AI logs, and system health.
- Added health-check scripts that generate Markdown and JSON reports under `reports/`.
- Added v1.4 HTTP regression tests for AI logs, AI chat sessions, and status-change attachment review.

### Not included in v1.4 scope

- Smart lesson preparation or teaching resource library.
- Course resource folders, video resource library, course clone, or resource push.
- Database backup, timed backup, restore, or backup download management.

## v1.3.0 - 2026-06-27

- Fixed login fallback behavior by adding safe default access and refresh token TTL values.
- Fixed multi-role display priority so admin/teacher/student users render and authorize consistently.
- Fixed AI service offline feedback by returning a readable Spring Cloud fallback message.
- Hardened AI safety configuration and content moderation reads against migration drift to avoid page-level 500 errors.
- Improved frontend error feedback on AI model administration, teaching evaluation details, notice management, registration applications, and status-change pages.
- Added real HTTP regression coverage for #39, #41, and #44.
- Added v1.3.0 QA closure documentation for issues #39-#59, including explicit out-of-scope module boundaries.

## v1.2 - 2026-06-27

- Added Spring Cloud OpenFeign, LoadBalancer, Nacos discovery wiring, AI model registry, search safety configuration, and release packaging.
- Closed the #4-#35 v1.2 issue matrix with backend tests, frontend build checks, Docker Compose verification, and release docs.
