# Context — DOC-SYSTEM-001

Inspected 2026-10-29 23:08–23:20 (+07:00) by Claude with file tools (no shell). `.git/HEAD` = `refs/heads/master` at inspection time.

## Current `GVRP_Document`

```
GVRP_Document/
  ENGINE_V1_TO_V2.md            (60 KB, 2026-10-08)  engine migration notes
  SOLUTION_DEEP_LINK.md         (2 KB, 2026-09-27)
  .idea/                        IDE metadata
  source/
    .idea/                      IDE metadata
    analysis/analysis class.puml                    (2025-10)
    design/entry_package_structure.txt              (2025-11)
    design/class/00_Styling … 08_Engine_API_Classes.puml, system_architecture.puml (2025-10 … 2026-09)
    design/component/entry_service_component_diagram.puml (2026-01)
    design/database/er_diagram.puml (2026-01), init.sql (2025-11)
    design/Engine Architecture/01_architecture … 04_activity.puml (2026-08)
    design/sequence_new/VRP-001_*, VRP-002, VRP-003_Failure_Callback, driver-delivery-*.puml
    design/sequence_old/VRP-001.puml, VRP-002.puml
```
None of these is checked against today's code here; most predate LEZ-001, PARETO-00x, DRIVER-*, SEC-* and the client v2. `sequence_old` is outdated by name. No rendered SVGs exist. No test-design documents exist.

## System inventory (starting points, verify in Step 3)

- Entry API controllers (`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/controller/`): Auth, BranchZone, Depot, Driver, Fleet, LezCompliance, LezMembership, OptimizationCallback, OptimizationJob, Order, Pareto, RouteSend, Solution, Vehicle, VehiclePermit, VehicleType, ZoneTemplate.
- Client v2 features (`GVRP_Entry_client_v2/src/app/features/`): admin (vehicle types, vehicles + permits, depots, low-emission zones), auth, depot, driver, fleet, jobs (planning dialog, monitoring, history), main (dashboard: map, orders, route, timeline, Pareto, compliance), solution-link, vehicle-type. Routes in `app.routes.ts`.
- Engine API (`GVRP_Engine_API/src`): GraphHopper/jsprit optimization, emission model, LEZ skills, callbacks/outbox.
- Proposed domains: D01 Authentication and roles; D02 Master data (depots, vehicle types incl. legal attributes, fleets and vehicles, drivers); D03 Orders (CRUD with geocoding, import, bulk status, filters/map); D04 Route planning (job submit, progress, cancel, history, engine callback, solutions); D05 Solution review and customization (route view, timeline, evaluate, revisions, Pareto); D06 Dispatch and driver delivery (send route, driver view); D07 Low-emission zones (templates, branch zones, rules, permits, membership, compliance, recheck, send guard); D08 Optimization engine; D09 Solution deep link; plus any domain found in code (e.g. the Analysis tab).

## Existing design knowledge outside `GVRP_Document`

- Closed work items (verify `state: done` with `workflow.mjs status`): BULK-EDIT-001, CI-CLIENT-001, DRIVER-DELIVERY-001, DRIVER-MAP-001, -002, -003, -004, PARETO-001, -002, -003, SEC-ROLE-001, SEC-SESSION-001, SEC-TENANT-001. PARETO-004 holds only `change-note.md` (no run). Their `design.md` / `request.md` / `closure.md` hold the accepted requirements and decisions of those features.
- Open work items (do not touch): LEZ-001 (`implement`; its run scope includes `GVRP_Document`, so its `recover` will report scoped-file changes — accepted by the user 23:12), MAP-ORDERS-001, BROWSER-FIX-001, UX-ORDERS-001, UX-LEZ-001 (`verify`). They may be **read** as sources (LEZ-001 `design.md`, `design-p1c.md`).
- References to work-item paths exist in `docs/engineering/LESSONS.md` (L-2026-001…013), `docs/security/SECURITY_CHECKLIST.md`, `docs/research/*`. These files are out of the engineer's scope; Claude updates them at closure using the index this task produces.
- `tools/session-brief.mjs` reads `work-items/*/run.json`; `tools/session-brief.test.mjs` and `tools/feature-workflow/workflow.test.mjs` exist.

## Tooling

- PlantUML: `.agents/skills/architecture-diagrams/.temp/plantuml.jar` (needs Java; JDK 21 is used for Maven, L-2026-007). Skill `.agents/skills/architecture-diagrams` (references for use case, class, component, sequence, state, activity, deployment, C4 via Structurizr) — `docs/workflows/business-development/README.md` asks to load it for design diagrams.
- Excel: Python 3.12 present on the machine (a `cpython-312` cache in `tools/__pycache__`); `openpyxl` presence not verified. No `.xlsx` generator exists in the repo.
- `.agents/skills/docx` exists (only if a `.docx` is ever needed; not required here).

## Relevant lessons

L-2026-005 (scope freeze), L-2026-006 (engineer runs workflow commands), L-2026-009 (CRLF), L-2026-014 (create runs on Windows).

## Not verified

- Which of the existing `.puml` files still match the code (Step 3 decides per file).
- Whether `.idea/` folders under `GVRP_Document` are tracked by git.
- Whether `master` contains the UX-LEZ-001 work (engineer records `git log -1` and `git branch --contains HEAD`).
