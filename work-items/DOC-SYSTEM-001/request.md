# Request — DOC-SYSTEM-001 (system design documentation in GVRP_Document)

Source: Claude session of 2026-10-29 (+07:00, Vietnamese), `/delegate`. Artifact in English (AGENTS.md §2).

| When | User message | Recorded meaning |
|---|---|---|
| 23:07 | "giờ tôi muốn thêm một task để cho opencode vẽ design của tất cả các nhánh nghiệp vụ và chức năng của hệ thống và đặt vào GVRP_Document, các công việc sẽ bao gồm: xoá các design outdates (nếu có); tổ chức lại cấu trúc thư mục; thêm các tài liệu design từ requirement analysis -> system design -> basic design -> test case design. Có thể là bao gồm các file PlantUML, excel, docs tuỳ chỉnh; xoá các work-items đã hoàn thiện và close, dịch chuyển các design về Document (nếu có)" | Document every business domain and function of the system in `GVRP_Document`, across four levels; remove outdated designs; reorganize folders; migrate designs out of closed work items and delete those work items. |
| 23:12 | AskUserQuestion answers: language **"Tiếng Anh"**; closed work items **"Xoá, git giữ lịch sử"**; open work items **"Làm ngay, không đụng item mở"**; test cases **"Excel .xlsx"** | English documents; delete closed work-item folders after migrating what is still useful, git history keeps them; do the documentation now but do not touch the five open work items; test-case design as `.xlsx` (generated from a diffable source). |

## Requirements

| ID | Requirement |
|---|---|
| DS-R1 | `GVRP_Document` has one documented structure with four levels — requirements analysis, system design, basic design, test design — plus an index (`README.md`) and a history area. |
| DS-R2 | Requirements analysis covers every business domain: actors and roles, use-case catalogue with stable IDs, business rules, non-functional requirements, use-case diagrams. |
| DS-R3 | System design covers context and containers (Client v2, Entry API, Engine API, MySQL, GraphHopper data, external services), deployment, integration flows (job dispatch, callbacks, outbox/retry), security and tenant model, data architecture (ER). |
| DS-R4 | Basic design covers, per domain: screens and screen flow, API list (method, path, role, request/response summary, errors), sequence diagrams of the main use cases, state diagrams of stateful entities, class/component views, table definitions. |
| DS-R5 | Test design: strategy, and a test-case catalogue in Excel (`.xlsx`) generated from a CSV source, traced to use cases and linked to existing automated tests where they exist. |
| DS-R6 | Every diagram is PlantUML source plus a rendered SVG, reverse-engineered from the current code; anything not verified against code is marked `ASSUMPTION`. |
| DS-R7 | Outdated designs are removed (git keeps them) and listed with the reason. |
| DS-R8 | Closed work items (state `done`) and PARETO-004 are deleted after their still-valid design content is migrated; an index records each deleted folder, its last commit and where its content went. |
| DS-R9 | The five open work items (LEZ-001, MAP-ORDERS-001, BROWSER-FIX-001, UX-ORDERS-001, UX-LEZ-001) are not modified, moved or deleted. |

## Exclusions

- Source code, tests, build files, `docs/` (research, engineering, security, workflows), `AGENTS.md`, `CLAUDE.md`, `.opencode/`, `.agents/` (Claude updates memory files at closure).
- Scientific claims (emission model validity, solver quality): documented as implemented, not as validated (AGENTS.md §4, §5).
- Real customer data, secrets, property values.

## Acceptance

- `GVRP_Document/README.md` lists every document; every `.puml` has a matching `.svg` rendered without error.
- Each domain has a use-case section, API table, at least one sequence diagram, and test cases in the Excel catalogue.
- `git status` shows deletions only for the closed work items listed in the index and for outdated design files listed in the history area; none of the five open work items changed.
