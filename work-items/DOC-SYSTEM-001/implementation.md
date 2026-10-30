# Implementation — DOC-SYSTEM-001

Engineers: OpenCode `gvrp-engineer` (Steps 0–1, Parts A–C and the D01/D02/D03/common READMEs, 2026-10-29 16:15 → 2026-10-30 07:38 +07:00), then Claude Code on the user's request "Opencode đã làm một ít rồi đó, bạn làm tiếp đi" (rest of Part D, Parts E–F, verification, this record, 2026-10-30). Windows host shell.
Branch: `docs/system-design-001`, created from `master` @ `c52bb17`. Nothing committed; deletions are staged (`git rm`), new files untracked.

## Scope

Documentation only, per `design.md`: a four-level documentation set in `GVRP_Document/` (requirements, system design, basic design per domain, test design), a history area, removal of outdated designs, and deletion of the 14 closed work items after migrating their still-valid content. No source, test, build, `docs/`, `tools/`, `.github/`, rule or agent file was changed.

## Baseline

| Check | Result | Source |
|---|---|---|
| `git log -1 --oneline`; `git branch --contains HEAD` | `c52bb17 …UX-LEZ-001…`; `master` | `GVRP_Document/README.md` (recorded by OpenCode) |
| `node --test tools/feature-workflow/workflow.test.mjs` | 10 tests, 10 pass, 0 fail | OpenCode recorded no figure; **reconstructed** by Claude on 2026-10-30 by running the tests of `git archive HEAD tools docs/workflows` in a scratch folder outside the repository (`tools/` is unchanged on the branch) |
| `node --test tools/session-brief.test.mjs` | 5 tests, 5 pass, 0 fail | same reconstruction |
| JDK and PlantUML | JDK 21 (`C:\Program Files\Java\jdk-21`); `.agents/skills/architecture-diagrams/.temp/plantuml.jar` renders (`-Djava.awt.headless=true`) | rendering of every diagram below |
| `py -c "import openpyxl"` | `ModuleNotFoundError: No module named 'openpyxl'` | run 2026-10-30 (also `python`) |
| Files under `GVRP_Document` | 33 tracked files at `c52bb17`, classified in Part A; `.idea/` folders untracked and left alone | `_history/removed-designs.md`, checkpoint A |

## Changes Made

### Structure (as delivered)

```
GVRP_Document/
  README.md
  01-requirements/   00-system-overview, 01-actors-and-roles, 02-use-case-catalogue, 03-business-rules,
                     04-non-functional (.md); diagrams/use-case-D01…D09 (.puml/.svg)
  02-system-design/  01-context-and-containers, 02-deployment, 03-integration, 04-security-and-tenancy,
                     05-data-architecture (.md); diagrams/c4-context, c4-container, deployment, sequence-auth,
                     sequence-dispatch-callback, er-model (.puml/.svg)
  03-basic-design/   common/README.md; D01-auth … D09-solution-link/README.md + diagrams/
  04-test-design/    01-test-strategy.md, test-cases.csv, tools/build_test_cases.py (GVRP_Test_Cases.xlsx NOT generated)
  _history/          work-item-index.md, removed-designs.md, engine-v1-to-v2-log.md, decisions/<14 ids>.md
  _shared/           puml-styling.puml (include file)
```

41 diagrams (`.puml`) each with a rendered `.svg`; `GVRP_Document/source/` no longer exists.

### Documents per level

| Level | By | Content |
|---|---|---|
| 1 | OpenCode | overview/glossary, actors and permission matrix, 48 use cases `UC-D01-01`…`UC-D09-01`, business rules `BR-001`…`BR-096`, non-functional, 9 use-case diagrams |
| 2 | OpenCode | context/containers, deployment, integration (dispatch, four callbacks, spool/retry, geocoding, OSRM, tiles), security and tenancy, data architecture (22 tables, FK matrix), 6 diagrams |
| 3 | OpenCode: `common`, D01, D02, D03 (+ their 6 diagrams). Claude: D04, D05, D06, D07, D08, D09 READMEs; diagrams `state-solution-revision`, `sequence-save-revision`, `sequence-pareto` (D05), `state-route-send` (D06), `class-lez`, `state-lez-status`, `sequence-compliance-recheck` (D07), `sequence-deep-link` (D09); rewrote `sequence-route-send` and `sequence-driver-view` (D06, outdated); translated the five moved D08 diagrams to English; added the four vehicle-permit endpoints to D02 | per domain: screens, API table, payloads, errors, data touched, sources |
| 4 | Claude | strategy; 155 test cases (D01 12, D02 20, D03 23, D04 20, D05 16, D06 19, D07 29, D08 13, D09 3), 122 linked to existing tests, 33 manual; generator script |

Corrections Claude made to OpenCode's earlier parts while verifying: `BR-096` (`SolutionStatus.INITIAL` is never written); the planning-preferences rule renumbered `BR-070` → `BR-059` (two rules shared `BR-070`); use-case ranges in D02/D03 READMEs; catalogue coverage table (D02 16, D06 4 endpoints); stale test-case ids and a non-existent diagram (`sequence-pareto.puml`, now created) in `_history/work-item-index.md` and `_history/decisions/*.md`; four stale replacement paths in `_history/removed-designs.md`; README include-path note.

### Removed designs

21 outdated files removed with `git rm`, 12 moved with `git mv` (11 renamed and modified, 1 renamed). Each removed file is listed with its last commit and reason in `GVRP_Document/_history/removed-designs.md` (by OpenCode, checked by Claude).

### Deleted work items (staged `git rm -r`)

| ID | State before deletion | Last commit | Index row |
|---|---|---|---|
| BULK-EDIT-001 | `done` rev 18 | `7a0c4ce` | `_history/work-item-index.md` |
| CI-CLIENT-001 | `done` rev 8 | `8851067` | idem |
| DRIVER-DELIVERY-001 | `done` rev 18 | `de58232` | idem |
| DRIVER-MAP-001 | `done` rev 13 | `de58232` | idem |
| DRIVER-MAP-002 | `done` rev 13 | `7a0c4ce` | idem |
| DRIVER-MAP-003 | `done` rev 10 | `7a0c4ce` | idem |
| DRIVER-MAP-004 | `done` rev 11 | `de58232` | idem |
| PARETO-001 | `done` rev 7 | `4e2f928` | idem |
| PARETO-002 | `done` rev 10 | `21d2805` | idem |
| PARETO-003 | `done` rev 7 | `21d2805` | idem |
| PARETO-004 | no run (`change-note.md` only) | `6dd718c` | idem |
| SEC-ROLE-001 | `done` rev 7 | `d67daa7` | idem |
| SEC-SESSION-001 | `done` rev 7 | `e98ec95` | idem |
| SEC-TENANT-001 | `done` rev 7 | `6160126` | idem |

321 files deleted under `work-items/`; the five open work items are untouched.

## Commands Executed

| Step | Command | Result |
|---|---|---|
| 0.1–0.4 | session brief, `git status`, `git log`, `git branch --contains`, `git switch -c docs/system-design-001` | run by OpenCode; exit codes not recorded in its checkpoints; outcome visible in the branch and `README.md` |
| 1 | `workflow.mjs init …` + `step … ready 01/02/03-proof.json` | `run.json`: rev 1 `discover`, rev 2 `design`, rev 3 `implement` (2026-10-29T16:15Z) |
| 2 | baseline | not recorded by OpenCode; reconstructed by Claude, see Baseline |
| 3 A | `workflow.mjs checkpoint … checkpoint-a.json` | rev 4 (OpenCode) |
| 3 B | `… checkpoint-b.json` | rev 5 (OpenCode) |
| 3 C | `… checkpoint-c.json` | rev 6 (OpenCode) |
| 3 D | `… checkpoint-d.json` | exit 0, rev 7 (Claude) |
| 3 E | `… checkpoint-e.json` | exit 0, rev 8 |
| 3 F | `… checkpoint-f.json` | exit 0, rev 9 |
| render | `java -Djava.awt.headless=true -jar plantuml.jar -tsvg <file>` per diagram (PowerShell, JDK 21) | 41 × exit 0. First run: 2 errors caused by Claude's own translation script inserting real line breaks for `\n`; fixed by re-translating from the `HEAD` originals. Six `@startuml <name>` lines produced SVGs under old names; names removed, orphan SVGs deleted |
| E | `py GVRP_Document/04-test-design/tools/build_test_cases.py` | exit 2: *openpyxl is not installed* |
| V6 | `node --test tools/feature-workflow/workflow.test.mjs`; `node --test tools/session-brief.test.mjs` | 10/10 and 5/5, exit 0 |
| V7 | `node tools/session-brief.mjs` | exit 0 |
| 4 | `workflow.mjs step … ready work-items/DOC-SYSTEM-001/04-proof-rev9.json` | the prepared `04-proof.json` carries `revision: 3`, but six checkpoints moved the run to rev 9; a copy with `revision: 9` and the same artifact is used, the original is left unchanged. Result in the handoff message |

## Verification Results

| ID | Result |
|---|---|
| DS-V1 | **Pass.** 67 endpoints in controllers (Entry 59, Engine 8) = 67 API rows; set comparison by method + path: none missing, none extra (script over `@*Mapping` annotations and README tables) |
| DS-V2 | **Pass.** All 13 client routes of `app.routes.ts` appear in a screen list (`/login` D01; `/setup/*`, `/admin/{vehicle-types,vehicles,depots}` D02; `/main` D03 and tab sub-screens in D05/D07; `/jobs`, `/jobs/:id` D04; `/driver` D06; `/admin/zones` D07; `''` D09). `/main` and `/driver` are also mentioned in D01 as redirect targets |
| DS-V3 | **Pass.** 41 `.puml` → 41 `.svg`, every render exit 0, no orphan SVG, no error text in any SVG (`_shared/puml-styling.puml` is an include file, not a diagram) |
| DS-V4 | **Pass.** All 48 use cases have ≥ 1 test case; every `use_case_id` exists; no duplicate `tc_id`; every test-case id cited in a `.md` file exists |
| DS-V5 | **Pass.** All 122 `automated_by` values resolve: `Class#method` found in `GVRP_Entry_API`/`GVRP_Engine_API` test sources, spec paths exist |
| DS-V6 | **Pass.** Same as baseline (10/10, 5/5) |
| DS-V7 | **Pass.** Brief runs; LEZ-001, MAP-ORDERS-001, BROWSER-FIX-001, UX-ORDERS-001, UX-LEZ-001 listed (plus DOC-SYSTEM-001) |
| DS-V8 | **Pass.** `git status` shows changes only under `GVRP_Document/**`, `work-items/DOC-SYSTEM-001/**`, and deletions of the 14 listed work items |
| DS-V9 | **Pass.** Regex scan of every `.md`, `.puml`, `.csv`, `.py`, `.svg` under `GVRP_Document` for `password/secret/api-key/token = <value>`, `jdbc:` with credentials and JWT-like strings: 0 hits. The only `password=` texts are the placeholders `<test password>` and `wrong` in two manual test cases |
| Link check | relative Markdown links and backticked document paths under `GVRP_Document`: 0 broken (two glob patterns `03-basic-design/*/README.md` are intentional) |

## Modified Tests

None.

## Security-Relevant Changes

- No secret value, `.env` content or credential property value in any document; configuration is named only (e.g. `entry.api-key`, `geocoding.goong.api-key`). DS-V9 result above.
- Data used: source code, migrations, existing tests and the read-only open work-item files. No `gvrp_db` business rows were read by Claude; test data in the catalogue is fictional. OpenCode's checkpoint C records `SELECT COUNT(*)` row figures in `02-system-design/05-data-architecture.md` — counts only, no row content (reported for the reviewer: design §3 allows schema queries only).
- Findings documented, not fixed (outside scope): D06 send answers a foreign-branch solution with a message that confirms the id exists; login with a wrong password answers `500`; D03–D06 answer unknown/foreign ids with `400` where the rules say `404`.

## Deviations From Design

1. Work split across two engineers; Steps 0–2 outcomes were not recorded by OpenCode, so the Step 2 baseline was reconstructed from `HEAD` (see Baseline).
2. `GVRP_Test_Cases.xlsx` not generated (openpyxl missing; design Part E says report, do not install).
3. `GET /api/v1/drivers` is documented in D02 (it reads users) although its use case is UC-D06-03.
4. D08 basic design has no screens section content and no API envelope (the engine has neither); its five diagrams were translated, not redrawn.
5. Earlier-part corrections listed under Changes Made (BR renumbering, BR-096, history references).

## Remaining Uncertainty

- `ASSUMPTION:` markers: `README.md` 3, `01-requirements/00-system-overview.md` 2, `01-requirements/01-actors-and-roles.md` 1, `01-requirements/04-non-functional.md` 1, `03-basic-design/D04-planning/README.md` 1 (failure callback is not spooled), `03-basic-design/D04-planning/diagrams/sequence-job-submit.puml` 1, `03-basic-design/D08-engine/README.md` 1 (`/legs` without `LegsService`). Total 10.
- Not rendered: nothing; XLSX not generated.
- The documentation describes the code at `c52bb17`; nothing was verified in a running system. Scientific statements are "as implemented", not validated.
- `tools/session-brief.test.mjs` contains the string `work-items/SEC-SESSION-001`; the test still passes after the deletion (it is fixture data), but the reference is now historical.

## Lesson Candidates

- A PlantUML `@startuml <name>` makes `-tsvg` write `<name>.svg` instead of `<file>.svg`; keep `@startuml` unnamed when the file name is the contract.
- Editing diagram text through a shell heredoc into Python turned `\n` into real line breaks; write such scripts to a file (or use the edit tool) and re-render immediately.

## Handoff to Reviewer

- Read first: `03-basic-design/D04…D09/README.md` (Claude's part), `04-test-design/test-cases.csv`, `_history/work-item-index.md`.
- Re-run: DS-V1/V4/V5 scripts are reproducible from the descriptions above; render with the command in `GVRP_Document/README.md`.
- References in `docs/` (and other tracked files) that point to deleted work items — for Claude to fix at closure:
  - `docs/security/SECURITY_CHECKLIST.md`: `work-items/SEC-TENANT-001` (×3), `work-items/SEC-TENANT-001/context.md`, `work-items/SEC-SESSION-001`, `work-items/SEC-ROLE-001`, `work-items/DRIVER-MAP-004/request.md`
  - `docs/engineering/LESSONS.md`: `work-items/SEC-ROLE-001/closure.md` (×3), `work-items/SEC-TENANT-001/closure.md` (×3), `work-items/SEC-TENANT-001`, `work-items/PARETO-001` (×2), `work-items/PARETO-002`, `work-items/DRIVER-MAP-004/verification-rereview.md`, `work-items/CI-CLIENT-001`
  - `docs/research/observations/OBJ-001-pareto-weight-carbon-multiplier-2026-10-20.md`: `work-items/PARETO-002/verification.md`
  - `.github/workflows/ci.yml`: `work-items/DRIVER-MAP-004/verification-rereview.md` (comment)
  - `tools/session-brief.test.mjs`: `work-items/SEC-SESSION-001` (fixture string)
- Also for closure: `docs/research/PROJECT_STATE.md` and `KNOWN_ISSUES.md` may cite the deleted folders; `traceability.md` of this work item still reads as planned.
