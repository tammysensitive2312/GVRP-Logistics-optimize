# Test strategy

How the system is tested today, and how the test-case catalogue (`test-cases.csv`) is organised and kept
true. The catalogue describes tests; it does not claim that a test passed. Every row starts with
`status = NOT_RUN`.

---

## 1. Levels

| Level | What it covers | Tooling (as present in the repository) | Runs where |
|---|---|---|---|
| `UNIT` | services, mappers, parsers, pure helpers; repositories and the engine client are Mockito substitutes; no database | JUnit 5, Mockito, AssertJ (`spring-boot-starter-test`) | `mvnw.cmd test` in `GVRP_Entry_API` / `GVRP_Engine_API`; CI |
| `WEB` | controller contract and security: status codes, error envelope, role rules, request binding | `MockMvcBuilders.standaloneSetup(...)` with `GlobalExceptionHandler`, or the security chain with mocked services | same as `UNIT` |
| `CLIENT` | Angular components, services, guards and pure utilities with spies; no real HTTP (`HttpTestingController` where a request is checked) | Jasmine + Karma, `ChromeHeadless` | `npx ng test --watch=false --browsers=ChromeHeadless` in `GVRP_Entry_client_v2`; CI |
| `MANUAL` | end-to-end behaviour in a browser against the local stack, and behaviour no automated test reaches (real geocoding, real engine, concurrency) | the running client (port 4200), Entry API (8080), Engine API (8081), local MySQL | a person, with the scenarios in this catalogue |

There is no automated end-to-end suite, no contract test between Entry and Engine, and no load test in the
repository. The two `@SpringBootTest` context tests need a MySQL instance; CI has none
(`docs/engineering/LESSONS.md` L-2026-001).

## 2. Environments and data

* Local development stack only; the database is the local Docker MySQL `gvrp_db`.
* Test data is **fictional**: branch names such as *Branch A* / *Branch B*, order codes such as `ORD-T001`,
  addresses such as *1 Example Street Ha Noi*, city-centre coordinates. No row is copied from `gvrp_db`.
* Two branches are needed for every tenant-isolation case: the caller's branch (A) and another branch (B)
  owning the resource.
* Credentials are referred to as `<test password>`; no secret appears in the catalogue.
* A manual run that writes data (orders, jobs, sends, zones) records every row it created
  (`docs/engineering/LESSONS.md` L-2026-012).

## 3. What every use case gets

| Kind | Rule |
|---|---|
| Main path | at least one case per use case (`DS-V4`) |
| Error path | the validation or refusal that the basic design lists for the use case |
| Tenant isolation | for every branch-owned resource (depots, vehicle types, vehicles, fleets, permits, orders, jobs, solutions, routes and sends, zones, compliance) a case where branch A addresses branch B's id; the expected result is the **implemented** status (`400` + `0040402` in D02–D06, `404` in D07), with *nothing read or written* |
| Role rule | planner/driver/customer/anonymous access for the domains with a role boundary (D01, D06, D07) |

Expected results state what the code does today. Where that differs from the business rule (for example
unknown ids answering `400` where the rule says `404`, or a wrong password answering `500`), the row says
*Implemented:* and the basic-design document names the gap.

## 4. Linking to automated tests

`automated_by` holds either `TestClass#method` (Java) or the repository path of a client spec file. A value is
written only when that test exists; the catalogue was checked against the source tree on 2026-10-30 (every
value resolved). An empty `automated_by` means the case is manual or not yet automated. The Java test level
(`UNIT` vs `WEB`) follows how the test is built, not what it is named.

## 5. Entry and exit criteria

| | Criterion |
|---|---|
| Entry | the modules build; the automated suites pass on the branch under test (`mvnw.cmd test`, `npx ng test`); the local stack starts |
| Exit, automated | every `UNIT`/`WEB`/`CLIENT` case's linked test passes in CI |
| Exit, manual | every `H`-priority `MANUAL` case run once on the release candidate, result recorded in the `status` column of a copy of the workbook (`PASS`, `FAIL`, `BLOCKED`) with the rows it created |

## 6. The workbook

`test-cases.csv` (UTF-8) is the source of truth and is reviewed in diffs. `tools/build_test_cases.py` turns it
into `GVRP_Test_Cases.xlsx`: one sheet per domain (frozen header, column widths, wrapped text), a
*Traceability* sheet (use case → test cases, including use cases without a test) and a *Summary* sheet
(counts per domain and level, automated vs not). The output is deterministic.

**Generation status (2026-10-30):** `openpyxl` is not installed in the documentation environment, so the
workbook was **not** generated; the script stops with exit code 2 and a message. Install `openpyxl` in a
personal Python environment (it is not a repository dependency) and run, from the repository root:

```powershell
py GVRP_Document/04-test-design/tools/build_test_cases.py
```

## 7. Catalogue at a glance

155 cases: D01 12, D02 20, D03 23, D04 20, D05 16, D06 19, D07 29, D08 13, D09 3; 122 linked to an existing
automated test, 33 manual. All 48 use cases of `01-requirements/02-use-case-catalogue.md` have at least one
case.

## Sources

`GVRP_Entry_API/src/test/java/**`, `GVRP_Engine_API/src/test/java/**`, `GVRP_Entry_client_v2/src/**/*.spec.ts`,
`.github/workflows/ci.yml`, `work-items/BROWSER-FIX-001/test-scenarios.md` (read-only), `03-basic-design/*/README.md`,
`docs/engineering/LESSONS.md`.
