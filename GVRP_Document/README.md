# GVRP Logistics Optimize — Documentation Set

Reverse-engineered documentation for the GVRP Logistics Optimize system, organised in four
levels: requirements analysis, system design, basic design and test design.

Every statement about the system is taken from the source code, database migrations and client
routes at the revision recorded below. Statements that could not be verified against the code are
marked `ASSUMPTION:` inline. This documentation describes **what the system does**; it does not
claim that the optimisation or emission model is scientifically validated (see `AGENTS.md` §4).

## Inspected revision

| Item | Value |
|---|---|
| `git log -1 --oneline` | `c52bb17 feat(UX-LEZ-001): zone drawing, rule help and presets, expandable route sections` |
| Branches containing `HEAD` | `master` |
| Documentation branch | `docs/system-design-001` (created from that commit; nothing committed) |
| Inspection date | 2026-10-29 (+07:00) |
| Scope | documentation only; no source, test or build file was changed |

Sources used: `GVRP_Entry_API/src/main/java/**`, `GVRP_Entry_API/src/main/resources/database/**`,
`GVRP_Engine_API/src/main/java/**`, `GVRP_Entry_client_v2/src/app/**`, plus the accepted decisions of
the closed work items listed in `_history/work-item-index.md`.

## Levels

| Level | Folder | Answers |
|---|---|---|
| 1. Requirements analysis | `01-requirements/` | Who uses the system, which use cases exist, which business rules constrain them, which non-functional limits apply |
| 2. System design | `02-system-design/` | Which containers exist, how they are deployed, how they integrate, how security and tenancy work, how data is structured |
| 3. Basic design | `03-basic-design/` | Per domain: screens, API, sequence and state behaviour, data touched |
| 4. Test design | `04-test-design/` | Test strategy, test-case catalogue, traceability to use cases and to existing automated tests |
| History | `_history/` | Removed designs, deleted work items and the decisions migrated out of them |

## ID conventions

| Prefix | Meaning | Example |
|---|---|---|
| `D01`…`D09` | Business domain | `D07` = Low-emission zones |
| `UC-Dxx-nn` | Use case | `UC-D03-02` |
| `BR-nnn` | Business rule | `BR-012` |
| `TC-Dxx-nnn` | Test case | `TC-D07-003` |
| `METHOD /path` | API row reference | `POST /api/v1/jobs/plan` |
| `ASSUMPTION:` | Statement not verified against the code | inline marker |

## Domains

The domain list starts from the inspection context and is corrected from the code. Corrections
recorded during this rebuild:

| Domain | Name | Correction |
|---|---|---|
| D01 | Authentication and roles | as proposed |
| D02 | Master data (depots, vehicle types, vehicles, permits, fleets) | fleet management and vehicle permits kept inside D02; 15 endpoints |
| D03 | Orders | as proposed |
| D04 | Route planning (jobs and engine callbacks) | callbacks included here, not in D08, because they are the Entry API side of the planning flow |
| D05 | Solution review and customisation (revisions, evaluation, Pareto) | as proposed |
| D06 | Dispatch and driver delivery | driver endpoints and route send are one domain: a send is the only way a driver receives work |
| D07 | Low-emission zones | zone templates, zones, rules, membership, compliance and the recheck |
| D08 | Optimisation engine (Engine API) | as proposed |
| D09 | Solution deep link | as proposed; no API of its own |

The "Analysis tab" mentioned in the inspection context is not a separate domain: the client's
`main` feature renders orders, routes, timeline, Pareto and compliance views for one solution.
`ASSUMPTION:` those views are read through the D03–D07 endpoints; no analysis-only endpoint exists.

## Rendering diagrams

Every `.puml` file has a rendered `.svg` next to it. Render from the repository root:

```powershell
java "-Djava.awt.headless=true" -jar .agents/skills/architecture-diagrams/.temp/plantuml.jar -tsvg <file>.puml
```

`-Djava.awt.headless=true` is required (the JAR otherwise takes foreground focus on some systems).
Quote it in PowerShell, which otherwise splits the argument at the `=`.

PlantUML resolves `!include` relative to the including file; the shared styling file is
`_shared/puml-styling.puml`, included as `!include ../../_shared/puml-styling.puml` from a
`<level>/diagrams/` folder and as `!include ../../../_shared/puml-styling.puml` from a
`03-basic-design/<domain>/diagrams/` folder.

Diagrams are written per `.agents/skills/architecture-diagrams/SKILL.md`: one purpose per diagram,
descriptive file names, generated SVGs committed next to their source.

## Conventions

- Language: English (diagram titles included). `_history/engine-v1-to-v2-log.md` is retained as an
  archive in its original language and is labelled as such.
- Every document ends with a **Sources** line naming the files it was read from.
- Configuration is documented by property **name** only. No secret values, no `.env` content, no
  customer data, no real customer coordinates.
- Database facts come from the migration files under
  `GVRP_Entry_API/src/main/resources/database/`; only schema queries were used, never business rows.
- Business rules are numbered `BR-nnn` in `01-requirements/03-business-rules.md` and referenced from
  the basic design instead of being restated.
- Status words (`VERIFIED`, `PARTIALLY_VERIFIED`, `NOT_VERIFIED`) mark how strongly a **claim about the
  code or configuration** is supported: read directly, inferred, or merely inspected. They are used in
  the requirements and system-design levels where a value could otherwise be mistaken for a requirement,
  and throughout the test design. They never assert that the optimisation or emission model is
  scientifically valid.

## Documents

Level 1 — requirements analysis

- [00-system-overview.md](01-requirements/00-system-overview.md)
- [01-actors-and-roles.md](01-requirements/01-actors-and-roles.md)
- [02-use-case-catalogue.md](01-requirements/02-use-case-catalogue.md)
- [03-business-rules.md](01-requirements/03-business-rules.md)
- [04-non-functional.md](01-requirements/04-non-functional.md)
- Use-case diagrams: `01-requirements/diagrams/use-case-D01.puml` … `use-case-D09.puml`, one per
  domain, each linked from its catalogue heading (each with an `.svg`)

Level 2 — system design

- [01-context-and-containers.md](02-system-design/01-context-and-containers.md)
- [02-deployment.md](02-system-design/02-deployment.md)
- [03-integration.md](02-system-design/03-integration.md)
- [04-security-and-tenancy.md](02-system-design/04-security-and-tenancy.md)
- [05-data-architecture.md](02-system-design/05-data-architecture.md)
- Diagrams: `02-system-design/diagrams/c4-context.puml`, `c4-container.puml`, `deployment.puml`,
  `sequence-auth.puml`, `sequence-dispatch-callback.puml`, `er-model.puml` (each with an `.svg`)

Level 3 — basic design (one folder per domain)

- [common/README.md](03-basic-design/common/README.md)
- [D01-auth/README.md](03-basic-design/D01-auth/README.md)
- [D02-master-data/README.md](03-basic-design/D02-master-data/README.md)
- [D03-orders/README.md](03-basic-design/D03-orders/README.md)
- [D04-planning/README.md](03-basic-design/D04-planning/README.md)
- [D05-solution/README.md](03-basic-design/D05-solution/README.md)
- [D06-dispatch/README.md](03-basic-design/D06-dispatch/README.md)
- [D07-lez/README.md](03-basic-design/D07-lez/README.md)
- [D08-engine/README.md](03-basic-design/D08-engine/README.md)
- [D09-solution-link/README.md](03-basic-design/D09-solution-link/README.md)
- Diagrams: listed in each domain README (sequence, state and class views)

Level 4 — test design

- [01-test-strategy.md](04-test-design/01-test-strategy.md)
- [test-cases.csv](04-test-design/test-cases.csv) — source of truth
- [tools/build_test_cases.py](04-test-design/tools/build_test_cases.py) — CSV → XLSX generator
- `GVRP_Test_Cases.xlsx` — generated workbook (see `04-test-design/01-test-strategy.md` for the
  current generation status)

History

- [_history/work-item-index.md](_history/work-item-index.md) — deleted work items and where their content went
- [_history/removed-designs.md](_history/removed-designs.md) — removed outdated design files
- [_history/engine-v1-to-v2-log.md](_history/engine-v1-to-v2-log.md) — archival engine migration log
- [_history/decisions/](_history/decisions/) — one short decision record per deleted work item

Shared

- `_shared/puml-styling.puml` — stereotype colours used by the class diagrams

IDE metadata under `GVRP_Document/.idea` and `GVRP_Document/source/.idea` is untracked and was left
untouched.
