# Academic-Nexus Issue Completion Matrix

Updated: 2026-06-27

This matrix records the closure scope through v1.4.0-final-polish. v1.2 closed #4-#35; v1.3.0 closed #39-#59; v1.4.0-final-polish focuses on #61-#74 final business closure and release polish.

## Final Verification

| Check | Result |
| --- | --- |
| Docker Compose config | `docker compose config` passed |
| Frontend audit | `npm audit` passed, 0 vulnerabilities |
| Frontend build | `npm run build` passed |
| Main backend tests | `.\mvnw.cmd test` passes, including v1.3.0 HTTP regression coverage |
| AI service tests | `..\mvnw.cmd test` in `ai-service` passed, no test sources |
| Spring Cloud config | Feign client, Nacos properties, and service-name wiring covered by tests and docs |
| v1.3.0 HTTP regression | `QaClosureHttpRegressionTests` covers #39, #41, and #44 non-500, permission, and AI fallback paths |
| v1.4.0 HTTP regression | `AiCallLogAdminRegressionTests`, `AiChatSessionRegressionTests`, `StatusChangeAttachmentAdminRegressionTests` passed |

## Issue Matrix

| Issue | Status | Evidence |
| --- | --- | --- |
| #4 | CLOSED BY V1.2 | Admin batch class transfer, class management homeroom binding, AI model/safety admin controls, docs and demo flow updated. |
| #5 | CLOSED BY V1.2 | Audit coverage added for course select/drop, student applications, evaluations, teacher exam updates, AI safety config, class batch transfer, and existing audit center pages. |
| #6 | CLOSED BY V1.2 | Backend role checks, menu permissions, teacher homeroom ownership guard, student class privacy DTO, permission matrix page, and regression tests. |
| #7 | CLOSED BY V1.2 | Existing Redis selection consistency checks retained; select/drop audit and interoperability regression tests passed. |
| #8 | CLOSED BY V1.2 | AI RAG sources, refusal paths, model registry, safety review, search blocking, and AI call logs covered by service tests and admin UI. |
| #9 | CLOSED BY V1.2 | Health center, Redis monitor, AI status, Flyway visibility, startup docs, and Spring Cloud verification guide updated. |
| #10 | CLOSED BY V1.2 | New endpoints follow existing `ApiResponse`, DTO, role guard, and service-layer patterns; release docs capture architecture boundaries. |
| #11 | CLOSED BY V1.2 | Redis course selection, cache fallback, consistency repair, and startup verification retained; docs identify Redis keys and deployment checks. |
| #12 | CLOSED BY V1.2 | Student class roster privacy, teacher homeroom authorization, AI/search safety, upload type config, and forbidden route checks documented. |
| #13 | CLOSED BY V1.2 | Backend tests expanded to 26, frontend production build passes, npm audit passes, AI module build passes, Compose config validated. |
| #14 | CLOSED BY V1.2 | Frontend menus/routes for missing teacher/student closure pages added, build passed, demo checklist updated for key UX states. |
| #15 | CLOSED BY V1.2 | Configurable content safety scenes, strategies, hit logs, admin APIs, tests, and UI integration completed. |
| #16 | CLOSED BY V1.2 | Existing grade publish/lock/change reason flow remains in demo script and audit coverage; teacher grade flow included in checklist. |
| #17 | CLOSED BY V1.2 | Database browsing, schema docs, migration notes, deployment guide, and issue matrix updated for v1.2. |
| #18 | CLOSED BY V1.2 | Academic profile, teaching plan, graduation audit and AI suggestions retained and covered in demo docs. |
| #19 | CLOSED BY V1.2 | Dockerfiles, Compose full stack, release build guide, startup guide, deployment guide, and release checklist completed. |
| #20 | CLOSED BY V1.2 | Shared page/header/status components retained; new pages use existing Element Plus/PageHeader patterns and build validation. |
| #21 | CLOSED BY V1.2 | Role dashboards and menus route users into student, teacher, and admin workflows; demo script documents role portal path. |
| #22 | CLOSED BY V1.2 | Admin pages for class, safety, model registry, health, audit, database, Redis and reports are part of final demo path. |
| #23 | CLOSED BY V1.2 | AI assistant, chat, model registry, safety review, search blocking, SQL guard, status display and logs form the trusted AI workspace. |
| #24 | CLOSED BY V1.2 | Student class page and teacher homeroom classes page added; core student/teacher flows are represented in menus and checklist. |
| #25 | CLOSED BY V1.2 | Demo checklist covers 403/404/login, role pages, upload rejection, sensitive word blocking, and key responsive/visual routes. |
| #26 | CLOSED BY V1.2 | Frontend upgrade orchestration completed to closure scope: missing routes added, npm audit fixed, production build validated. |
| #27 | CLOSED BY V1.2 | Unified issue orchestration captured in this matrix, startup docs, deployment guide, demo script, and final PR/release scope. |
| #30 | CLOSED BY V1.2 | JDBC charset fixed to `UTF-8`, startup/deployment docs include clean database and Flyway drift guidance, tests validate migrations. |
| #31 | CLOSED BY V1.2 | Spring Cloud dependencies, Nacos discovery, OpenFeign client and service-name invocation implemented. |
| #32 | CLOSED BY V1.2 | Docker Compose enables Nacos registration/discovery for both Java services and documents the manual verification flow. |
| #33 | CLOSED BY V1.2 | Three-role business closure improved with student class info, teacher homeroom classes, privacy guards, menu wiring and tests. |
| #34 | CLOSED BY V1.2 | AI model registry, search provider/safety review, sensitive query blocking and logs are implemented and covered by tests/docs. |
| #35 | CLOSED BY V1.2 | Student status-change attachment upload flow is present in the existing page/API and included in the demo checklist. |
| #39 | CLOSED BY V1.3.0 | Student applications, notice publish, AI safety config and AI fallback endpoints have non-500 regression coverage and frontend failure feedback. |
| #40 | CLOSED BY V1.3.0 | Three-role core flow acceptance is captured in `docs/qa/v1.3-issue-closure-report.md`. |
| #41 | CLOSED BY V1.3.0 | Anonymous/student forbidden states and multi-role ADMIN priority are covered by `QaClosureHttpRegressionTests`. |
| #42 | CLOSED BY V1.3.0 | Existing application, attachment, review and audit paths remain in scope; status-change and registration submits are covered. |
| #43 | CLOSED BY V1.3.0 | Existing course selection, drop, schedule and grade consistency test coverage remains part of the full suite. |
| #44 | CLOSED BY V1.3.0 | AI offline/service-discovery fallback now returns readable status and is regression-tested. |
| #45 | CLOSED BY V1.3.0 | Admin notice and AI safety config non-500 paths are covered; admin AI page now handles partial failures. |
| #46 | CLOSED BY V1.3.0 | Docker Compose config, build checks and release packaging are listed in the v1.3.0 QA report. |
| #47 | CLOSED BY V1.3.0 | Existing class, roster, transfer and teacher homeroom class paths are accepted as the v1.3.0 closure scope. |
| #48 | CLOSED BY V1.3.0 | Existing exam arrangement and student exam query paths are accepted; full classroom-resource CRUD is documented as outside v1.3.0 expansion. |
| #49 | CLOSED BY V1.3.0 | Evaluation detail reset now provides feedback and load errors are surfaced. |
| #50 | CLOSED BY V1.3.0 OUT OF SCOPE | Full graduation thesis management has no complete existing entry point and is documented as outside v1.3.0. |
| #51 | CLOSED BY V1.3.0 | Existing grade publish, student query, teacher/admin update and audit paths are retained. |
| #52 | CLOSED BY V1.3.0 | Existing academic warning, teaching plan, graduation audit and AI academic profile paths are retained. |
| #53 | CLOSED BY V1.3.0 | Notice publish non-500 regression coverage and notice page error feedback were added. |
| #54 | CLOSED BY V1.3.0 | Current timetable and exam schedule sync paths are accepted; full schedule-change workflow is outside v1.3.0 expansion. |
| #55 | CLOSED BY V1.3.0 OUT OF SCOPE | Course materials and homework workflow have no complete existing entry point and are documented as outside v1.3.0. |
| #56 | CLOSED BY V1.3.0 OUT OF SCOPE | Leave, attendance and absence-warning workflow is documented as outside v1.3.0. |
| #57 | CLOSED BY V1.3.0 | Existing student profile, information visibility and admin user/class paths are retained. |
| #58 | CLOSED BY V1.3.0 OUT OF SCOPE | Internship/practice workflow has no complete existing entry point and is documented as outside v1.3.0. |
| #59 | CLOSED BY V1.3.0 | Existing select/drop, Redis consistency and repair paths are accepted; waitlist/admin adjustment expansion is outside v1.3.0. |
| #61 | CLOSED BY V1.4.0 | Docker host backend port defaults to `8088`, internal container port remains `8080`, `MAIN_HOST_PORT` override documented. |
| #62 | CLOSED BY V1.4.0 | AI call logs support keyword, username, functionType, success, level, start/end time, serviceMode and traceId. |
| #63 | CLOSED BY V1.4.0 | `course_grab_panel.py` defaults to backend API mode and supports configurable MySQL direct mode. |
| #64 | CLOSED BY V1.4.0 | Admin status-change attachment list, preview and download endpoints added; role boundaries covered by regression tests. |
| #65 | CLOSED BY V1.4.0 | AI chat sessions, messages, model selection, history persistence and model-aware logs added. |
| #66 | CLOSED BY V1.4.0 | Batch task center, data archive records, database templates and CSV export added; excluded resource/backup modules documented. |
| #67 | CLOSED BY V1.4.0 | Visible system naming uses “教学综合信息服务平台”. |
| #68 | CLOSED BY V1.4.0 | Existing admin class/student CRUD, transfer and import flow retained and documented as covered. |
| #69 | CLOSED BY V1.4.0 | Demo data covers multiple colleges, majors, classes, teachers, students, courses, offerings, selections, grades and exams. |
| #70 | CLOSED BY V1.4.0 | State transition table documented in `docs/qa/v1.4-final-polish-report.md`. |
| #71 | CLOSED BY V1.4.0 | Redis/DB/cache consistency checks documented and existing consistency page retained. |
| #72 | CLOSED BY V1.4.0 | Audit traceability updated for AI, database templates, archive cleanup and attachment flows. |
| #73 | CLOSED BY V1.4.0 | New pages use loading, empty and error states; existing global 401/403 handling retained. |
| #74 | CLOSED BY V1.4.0 | Health-check scripts generate Markdown/JSON reports under `reports/`. |

## Remaining Release Discipline

Only close GitHub issues after the final PR is merged or accepted by the maintainer. The old revert PR #29 must remain untouched unless the maintainer explicitly decides to close it.
