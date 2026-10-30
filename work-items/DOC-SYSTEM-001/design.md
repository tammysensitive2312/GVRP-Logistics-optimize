# Design — DOC-SYSTEM-001

Inputs: `request.md` (DS-R1…R9), `context.md`. Documentation-only change by Claude's specification, 2026-10-29. All documents in English. Every statement about the system is taken from the code at the recorded revision; what cannot be verified is marked `ASSUMPTION:` inline.

## 1. Target structure

```
GVRP_Document/
  README.md                      index: purpose, levels, ID conventions, how to render, document list
  01-requirements/
    00-system-overview.md        purpose, scope, glossary
    01-actors-and-roles.md       actors, roles, permission matrix (role × function)
    02-use-case-catalogue.md     all UC-Dxx-nn with actor, goal, trigger, main flow, alternatives, rules
    03-business-rules.md         BR-nn (validation, tenant isolation, D4 one date per job, LEZ rules, status transitions…)
    04-non-functional.md         performance limits, security, logging, i18n, browser support — as implemented
    diagrams/use-case-<domain>.puml/.svg
  02-system-design/
    01-context-and-containers.md (+ diagrams/c4-context, c4-container)
    02-deployment.md             (+ diagrams/deployment)
    03-integration.md            Entry↔Engine dispatch, callbacks (completion/failure/cancel/progress), outbox and retry, geocoding, routing/OSRM, tiles
    04-security-and-tenancy.md   authentication, roles, branch scoping, secrets by property name only
    05-data-architecture.md      ER diagram, table list, migrations location
  03-basic-design/
    D01-auth/ … D09-solution-link/   one folder per domain, each with:
      README.md                  screens, screen flow, API table, errors, data touched, links to diagrams
      diagrams/*.puml/.svg       sequence (main use cases), state (stateful entities), class/component
    common/                      shared conventions: error format, pagination, naming, DTO snake_case
  04-test-design/
    01-test-strategy.md          levels (unit, web-layer, client spec, manual/browser), environments, data, entry/exit criteria
    test-cases.csv               source of truth (UTF-8)
    GVRP_Test_Cases.xlsx         generated: one sheet per domain + "Traceability" + "Summary"
    tools/build_test_cases.py    CSV → XLSX generator
  _history/
    work-item-index.md           deleted work items: ID, title, state, last commit touching it, where its content went
    removed-designs.md           removed outdated files: path, last commit, reason
    decisions/                   decisions migrated from closed work items (one file per work item, short)
```

IDs: domains `D01…D09`; use cases `UC-D03-02`; business rules `BR-012`; API rows referenced by `METHOD path`; test cases `TC-D03-007`. Domain list starts from `context.md` and is corrected from the code (add or merge domains as found; record changes in `README.md`).

## 2. Work parts (checkpoint after each)

**Part A — inventory and cleanup**
1. Record `git log -1 --oneline`, `git branch --contains HEAD`, and the inspected revision in `README.md`.
2. For every existing file in `GVRP_Document`: compare with the code; classify `KEEP` (move into the new structure, update if needed), `UPDATE`, or `OUTDATED`. Delete `OUTDATED` files (including `source/design/sequence_old/`), list them in `_history/removed-designs.md` with last commit (`git log -1 --format=%h -- <path>`) and reason. `ENGINE_V1_TO_V2.md` and `SOLUTION_DEEP_LINK.md`: move their still-valid content into the new structure; the migration log part goes to `_history/`.
3. `.idea/` folders: leave untouched; record whether they are tracked (`git ls-files GVRP_Document/.idea`).
4. Closed work items: for each ID in `context.md`, run `node tools/feature-workflow/workflow.mjs status work-items/<ID>/run.json` and proceed only if `state` is `done` (PARETO-004: no run, proceed). Extract still-valid requirements, decisions and diagrams into `01-requirements`, `03-basic-design` and `_history/decisions/<ID>.md` (short: problem, decision, date, source). Record in `_history/work-item-index.md`: ID, title, state, `git log -1 --format=%h -- work-items/<ID>`, destination of each migrated item. Then `git rm -r work-items/<ID>` (staged deletion is allowed; committing is not). Any non-`done` state → do not delete, report.

**Part B — requirements analysis** (`01-requirements`, DS-R2).

**Part C — system design** (`02-system-design`, DS-R3).

**Part D — basic design** (`03-basic-design`, DS-R4): every controller endpoint appears in exactly one domain's API table (check: count endpoints in controllers = rows); every client route appears in one domain's screen list; state diagrams at least for order status, optimization job status, solution/revision, route LEZ status, route send.

**Part E — test design** (`04-test-design`, DS-R5):
- CSV columns: `tc_id, domain, use_case_id, requirement_ref, title, level (UNIT|WEB|CLIENT|MANUAL), preconditions, test_data, steps, expected_result, priority (H|M|L), automated_by (test class#method or spec file, or empty), status (NOT_RUN)`.
- At least the main and the error paths of every use case; tenant isolation cases for every branch-owned resource; LEZ cases reuse `work-items/BROWSER-FIX-001/test-scenarios.md` and the LEZ-001 review scenarios (read-only sources).
- `automated_by` only for tests that exist (verify by file search); never invent a test name.
- Test data fictional (no rows copied from `gvrp_db`).
- `build_test_cases.py`: stdlib `csv` + `openpyxl`; one sheet per domain, frozen header, column widths, wrap text; "Traceability" sheet (use case → test cases); "Summary" sheet (counts per domain and level). Deterministic output. If `openpyxl` is not installed: stop Part E's generation step and report (do not install packages, do not add a repository dependency); the CSV and the script are still delivered.

**Part F — render and index**: render every `.puml` to `.svg` with `java -jar .agents/skills/architecture-diagrams/.temp/plantuml.jar -tsvg <file>`; zero render errors; `README.md` lists every document and diagram.

## 3. Rules

- Reverse-engineer from source (controllers, services, entities, migrations, client routes/components, Engine); do not invent classes, endpoints or flows. Cite the source file in each document's "Sources" line.
- Diagrams follow the `architecture-diagrams` skill (load it first).
- Configuration documented by property **name** only; no secret values, no `.env` content.
- No customer data, coordinates of real customers, tokens.
- DB: only the read-only `agent_reader` user and only schema queries (`information_schema`, `SHOW CREATE TABLE`); prefer migration files. No `SELECT` of business rows.

## 4. Verification

| ID | Check | Expected |
|---|---|---|
| DS-V1 | Endpoint count in controllers vs rows in all API tables | equal; differences listed |
| DS-V2 | Client routes in `app.routes.ts` vs screen lists | all present |
| DS-V3 | Every `.puml` rendered | one `.svg` per `.puml`, no error |
| DS-V4 | Every UC has ≥ 1 TC; every TC's `use_case_id` exists | true |
| DS-V5 | Every `automated_by` value exists in the repo | true |
| DS-V6 | `node --test tools/feature-workflow/workflow.test.mjs` and `node --test tools/session-brief.test.mjs` after the deletions | same result as baseline |
| DS-V7 | `node tools/session-brief.mjs` after the deletions | runs; the five open work items still listed |
| DS-V8 | `git status --short` | changes only in `GVRP_Document/**` and deletions of the listed closed work items (plus this work item) |
| DS-V9 | Secret scan of `GVRP_Document` (search for `password=`, `api-key`, `secret`, `jdbc:` with credentials, JWT-like strings) | no hit with a value |
