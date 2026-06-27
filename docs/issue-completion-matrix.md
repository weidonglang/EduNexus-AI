# Academic-Nexus v1.2 Issue Completion Matrix

Updated: 2026-06-27

This matrix tracks the v1.2 two-round closure plan. Round 1 focuses on backend business closure, role linkage, permissions, audit coverage, and AI safety switches. Round 2 should finish frontend experience work, deployment packaging, demo scripts, npm audit, release notes, and final issue closure.

## Round 1 Completed Scope

| Area | Status | Evidence |
| --- | --- | --- |
| Class and homeroom teacher linkage | DONE | `academic_class.homeroom_teacher_user_id`, admin class create/update binding, teacher homeroom class APIs, student class summary API |
| Batch class operations | DONE | Existing batch import/assignment retained; new batch transfer endpoint with per-student error details |
| Demo JDBC startup default | DONE | Default MySQL JDBC URLs now use Java charset `UTF-8` instead of MySQL charset name `utf8mb4` |
| Student privacy in class roster | DONE | `/api/students/me/class` returns only student number, name, and status for classmates |
| Teacher homeroom authorization | DONE | Teacher class roster access checks `academic_class.homeroom_teacher_user_id` against current username |
| Configurable AI/content safety | DONE | `ai_safety_config`, `/api/admin/ai/safety/config`, `/test`, `/hits`, and configured moderation in AI, search, notices, applications, and evaluations |
| Key audit additions | DONE | Course select/drop, student applications, evaluation submit, teacher exam changes, AI safety config, class batch transfer |
| Regression tests | DONE | Maven test suite expanded from 22 to 24 tests covering AI safety and homeroom/student class linkage |
| Frontend Round 1 wiring | PARTIAL | Admin class form supports homeroom teacher account; AI model admin page displays and edits safety strategies |

## Open Issue Matrix

| Issue | Title | Round 1 Status | Notes / Round 2 Remaining |
| --- | --- | --- | --- |
| #33 | Three-role business linkage audit | PARTIAL | Round 1 closes the class/homeroom/student privacy/backend governance portion. Remaining: full frontend teacher/student pages, notifications for exam/teacher changes, complete demo docs. |
| #30 | Demo startup config and Flyway checksum drift | PARTIAL | Default JDBC URLs in app config, `.env.example`, and release packaging now use `characterEncoding=UTF-8`. Round 2 should still verify a real MySQL demo startup and document checksum repair/reset steps. |
| #27 | #4-#25 unified orchestration | PARTIAL | This document is the Round 1 tracking artifact. Final orchestration and issue closure belongs to Round 2. |
| #26 | Frontend upgrade orchestration | PENDING | Round 2 owns the broad frontend pass. |
| #25 | Accessibility and responsive checklist | PENDING | Round 2 should test keyboard, responsive, and visual states. |
| #24 | Student and teacher core page redesign | PARTIAL | Backend endpoints exist for teacher homeroom and student class info; polished pages still remain. |
| #23 | AI workspace and trusted AI UX | PARTIAL | Safety config UI exists; broader AI workbench UX and feedback flows remain. |
| #22 | Admin signature page redesign | PARTIAL | Admin class and AI safety wiring updated; full redesign remains. |
| #21 | Role dashboard and Bento portal | PENDING | Round 2 frontend scope. |
| #20 | Design system and shared components | PENDING | Round 2 frontend foundation scope. |
| #19 | Deployment, one-click start, Docker, demo scripts | PENDING | Round 2 packaging/release scope. |
| #18 | Academic progress and teaching plan comparison | UNCHANGED | Existing coverage remains from previous work; not expanded in Round 1. |
| #17 | Data dictionary and database design docs | PARTIAL | Safety/class schema added; docs still need full data dictionary refresh. |
| #16 | Grade sensitive operation protection | PARTIAL | Existing grade reason/lock/change log remains; no new grade release workflow in Round 1. |
| #15 | Content safety and sensitive word checks | DONE FOR ROUND 1 | Configurable scenes, admin APIs, hit logs, AI/search/student content/notice integration implemented. |
| #14 | Frontend UX states | PARTIAL | Small admin page wiring only; full UX polish remains. |
| #13 | Automated tests and CI | PARTIAL | Local backend tests and frontend build pass; CI workflow audit remains. |
| #12 | Security hardening | PARTIAL | Student class privacy and AI/search safety improved; token invalidation and broader upload review remain. |
| #11 | Performance and cache governance | PARTIAL | New class/course cache eviction paths retained; full cache metrics/index review remains. |
| #10 | Backend engineering refactor | PARTIAL | New endpoints follow existing ApiResponse/ErrorCode patterns; no broad DTO refactor in Round 1. |
| #9 | System health and observability | UNCHANGED | Existing Spring Cloud warning observed in tests; no new observability work in Round 1. |
| #8 | AI credibility and feedback | PARTIAL | AI input/output safety and existing confidence/source behavior remain; feedback analytics and new AI scenes remain. |
| #7 | Course rules and grab consistency | PARTIAL | Select/drop audit added; existing anti-overfill/time-conflict tests pass. Full consistency report remains. |
| #6 | Permission matrix and anti-overreach | PARTIAL | Teacher homeroom ownership and student class privacy added with tests. Full matrix and more 403 tests remain. |
| #5 | Audit center 2.0 | PARTIAL | More critical write operations now audited. Full before/after state, request metadata, and export audit remain. |
| #4 | Admin efficiency and AI teaching aid | PARTIAL | Batch class transfer and AI safety admin controls added. Batch approval/export and teaching-aid resource flows remain. |

## Verification

| Check | Result |
| --- | --- |
| Backend tests | `./mvnw.cmd test` -> 24 tests passed |
| Frontend build | `npm run build` in `frontend` -> passed |
| Flyway migration validation | V1-V13 applied successfully in test H2 database |

## Closure Guidance

Only #15 is safe to close from the Round 1 branch if maintainers accept the configurable safety implementation as complete. The broader orchestration issues (#4-#14, #18, #20-#27, #33) should stay open until Round 2 finishes frontend UX, docs, demo startup, packaging, and release validation.
