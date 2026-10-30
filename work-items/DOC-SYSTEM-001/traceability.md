# Feature traceability

Feature ID: DOC-SYSTEM-001
Inspected source revision: working tree at `master` (`.git/HEAD`), 2026-10-29 23:08–23:20 +07:00
Request source: `work-items/DOC-SYSTEM-001/request.md` (user messages 2026-10-29 23:07, 23:12)

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| DS-R1 | Four-level structure + index + history (23:07) | §1 | Planned: `GVRP_Document/README.md`, folders | DS-V8; review | None | NOT_VERIFIED |
| DS-R2 | Requirements analysis per domain | §2 Part B | Planned: `01-requirements/*` | DS-V4; review | None | NOT_VERIFIED |
| DS-R3 | System design | §2 Part C | Planned: `02-system-design/*` | review | None | NOT_VERIFIED |
| DS-R4 | Basic design per domain | §2 Part D | Planned: `03-basic-design/D*/` | DS-V1, DS-V2 | None | NOT_VERIFIED |
| DS-R5 | Test design in Excel (23:12) | §2 Part E | Planned: `04-test-design/*` | DS-V4, DS-V5 | None | NOT_VERIFIED |
| DS-R6 | PlantUML + SVG, from code | §2 Part F, §3 | Planned: `**/diagrams/*.puml/.svg` | DS-V3 | None | NOT_VERIFIED |
| DS-R7 | Outdated designs removed and listed | §2 Part A | Planned: `_history/removed-designs.md` | DS-V8 | None | NOT_VERIFIED |
| DS-R8 | Closed work items deleted after migration (23:12) | §2 Part A | Planned: `_history/work-item-index.md`, `_history/decisions/*` | DS-V6, DS-V7, DS-V8 | None | NOT_VERIFIED |
| DS-R9 | Open work items untouched (23:12) | §2 Part A | — | DS-V8 | None | NOT_VERIFIED |

## Decision log

| Decision | Source | Affected |
|---|---|---|
| English documents | User 23:12 | all |
| Delete closed work items; git keeps history; index with last commit | User 23:12 | DS-R8 |
| Do it now; do not touch the five open work items; LEZ-001 scope change accepted | User 23:12 | DS-R9 |
| Test cases: CSV source + generated XLSX; no package installation by the agent | User 23:12; Claude | DS-R5 |
| `docs/` memory files updated by Claude at closure, not by the engineer | `/delegate` rules, AGENTS.md §16 | DS-R8 |
