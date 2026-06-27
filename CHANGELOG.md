# Changelog

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

