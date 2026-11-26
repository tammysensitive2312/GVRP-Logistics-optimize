# Test strategy

How the system is tested today, and how the test-case catalogues ([test-cases.csv](test-cases.csv) and [system-test-cases.csv](system-test-cases.csv)) are organised and kept true. The catalogues describe tests; they do not claim that a test passed. Every row starts with `status = NOT_RUN`.

---

## 1. Levels

| Level | What it covers | Tooling (as present in the repository) | Runs where |
|---|---|---|---|
| `UNIT` | services, mappers, parsers, pure helpers; repositories and the engine client are Mockito substitutes; no database | JUnit 5, Mockito, AssertJ (`spring-boot-starter-test`) | `mvnw.cmd test` in `GVRP_Entry_API` / `GVRP_Engine_API`; CI |
| `WEB` | controller contract and security: status codes, error envelope, role rules, request binding | `MockMvcBuilders.standaloneSetup(...)` with `GlobalExceptionHandler`, or the security chain with mocked services | same as `UNIT` |
| `CLIENT` | Angular components, services, guards and pure utilities with spies; no real HTTP (`HttpTestingController` where a request is checked) | Jasmine + Karma, `ChromeHeadless` | `npx ng test --watch=false --browsers=ChromeHeadless` in `GVRP_Entry_client_v2`; CI |
| `MANUAL` | domain-level scenarios in a browser or HTTP client against the local stack not yet linked to a unit/web/client test | the running client (port 4200), Entry API (8080), Engine API (8081), local MySQL | a person, with the scenarios in [test-cases.csv](test-cases.csv) |
| `SYSTEM` | full-stack end-to-end business flows, cross-branch security/tenancy, cross-service resilience, and boundary/resource limits (`API_AUTO_TARGET` + `BROWSER_E2E`) | running local stack (`Entry :8080`, `Engine :8081`, MySQL, `Client v2 :4200`) per [02-system-test-plan.md](02-system-test-plan.md) | local system-test run (`system-test-cases.csv`) |

[02-system-test-plan.md](02-system-test-plan.md) defines the planned system-test design and hybrid execution target (`API_AUTO_TARGET` + `BROWSER_E2E`); automated end-to-end test scripts are not yet implemented in the repository. The two `@SpringBootTest` context tests need a MySQL instance; CI has none (`docs/engineering/LESSONS.md` L-2026-001).

## 2. Environments and data

* Local development stack only; system tests prefer an isolated `gvrp_test_db` schema (fallback: `gvrp_db` with `ST-` prefixed entities and cleanup ledger).
* Test data is **fictional**: branch names such as *Branch A* / *Branch B* (`ST-BRANCH-A` / `ST-BRANCH-B`), order codes such as `ORD-T001` / `ST-ORD-001`, addresses such as *1 Example Street Ha Noi*, city-centre coordinates. No row is copied from `gvrp_db`.
* Two branches are needed for every tenant-isolation case: the caller's branch (A) and another branch (B) owning the resource.
* Credentials are referred to as `<test password>` / `<test-password>`; no secret appears in the catalogues.
* A live verification run that writes data (orders, jobs, sends, zones) records every row it created and cleans up per `docs/engineering/LESSONS.md` L-2026-012.

## 3. What every use case gets

| Kind | Rule |
|---|---|
| Main path | at least one case per use case (`DS-V4`) |
| Error path | the validation or refusal that the basic design lists for the use case |
| Tenant isolation | for every branch-owned resource (depots, vehicle types, vehicles, fleets, permits, orders, jobs, solutions, routes and sends, zones, compliance) a case where branch A addresses branch B's id; the expected result across `D01`–`D07` and `D09` is uniformly `404` + `0040402` (`docs/engineering/EXCEPTION_CONVENTIONS.md`), with *nothing read or written* |
| Role rule | planner/driver/customer/anonymous access for the domains with a role boundary (D01, D06, D07) |

Expected results state what the code does today (`master @ cebf7f3`, post PR 1–5: wrong password returns `401 + 0040101`, conflicts return `409 + 0040901`, self-register returns `403 + 0040301`, pagination/limit violations return `400 + 0040002`, and Engine endpoints require `X-Engine-Key` except exact `GET /health`). Where an implemented behavior still differs from the desired rule (such as `TC-D03-008` duplicate `order_code` returning `500` on single-order `POST` or `TC-D02-007` dead Delete returning `405`), the row says *Implemented:* and documents the gap.

## 4. Linking to automated tests

`automated_by` holds either `TestClass#method` (Java) or the repository path of a client spec file. A value is written only when that test exists; the catalogue was re-verified against the source tree on `2026-11-23` after PR 1–5 (every value resolved). An empty `automated_by` means the case is manual or not yet automated. The Java test level (`UNIT` vs `WEB`) follows how the test is built, not what it is named.

## 5. Entry and exit criteria

| | Criterion |
|---|---|
| Entry | the modules build cleanly (JDK 21 / Node 22); the automated suites pass on the branch under test (`mvnw.cmd clean verify`, `npx ng test`); CSV validation (`--validate-only`) and secret scan pass; the local stack starts |
| Exit, automated | every `UNIT`/`WEB`/`CLIENT` case's linked test passes in CI |
| Exit, manual / system | every `H`-priority `MANUAL` and `SYSTEM` (`ST-*`) case executed on the release candidate with result recorded (`PASS`, `FAIL`, `BLOCKED`), 0 open tenant-isolation/security defects, and created DB rows cleaned per `L-2026-012` |

## 6. The workbook

`test-cases.csv` and `system-test-cases.csv` (UTF-8) are the sources of truth and are reviewed in diffs. `tools/build_test_cases.py` validates both catalogues (`--validate-only`, no external dependencies) and turns `test-cases.csv` into `GVRP_Test_Cases.xlsx` (or `system-test-cases.csv` into `GVRP_System_Test_Cases.xlsx` with `--system`): one sheet per domain or pillar (frozen header, column widths, wrapped text), a *Traceability* sheet (use case → test cases, including use cases without a test) and a *Summary* sheet. The output is deterministic.

**Generation status (`2026-11-23`):** `openpyxl` is not a repository dependency, so generated `.xlsx` workbooks are not tracked in git. Validate CSV integrity or build workbooks in a personal Python environment from the repository root:

```powershell
py GVRP_Document/04-test-design/tools/build_test_cases.py --validate-only
py GVRP_Document/04-test-design/tools/build_test_cases.py
py GVRP_Document/04-test-design/tools/build_test_cases.py --system
```

## 7. Catalogue at a glance

* [test-cases.csv](test-cases.csv) (**164 cases**): `D01` 12, `D02` 23, `D03` 24, `D04` 21, `D05` 16, `D06` 19, `D07` 33, `D08` 13, `D09` 3 — **136 linked to an existing automated test, 28 manual**. Every use case satisfies `DS-V4` (at least one positive main-path case).
* [system-test-cases.csv](system-test-cases.csv) (**54 cases**): `E2E` 16, `SEC` 14, `RES` 14, `LIM` 10 (`API_AUTO_TARGET` 37, `BROWSER_E2E` 17; `H` 39, `M` 15, `L` 0).
* All **48 use cases** of [02-use-case-catalogue.md](../01-requirements/02-use-case-catalogue.md) have coverage across both catalogues.

## Sources

`GVRP_Entry_API/src/test/java/**`, `GVRP_Engine_API/src/test/java/**`, `GVRP_Entry_client_v2/src/**/*.spec.ts`,
`.github/workflows/ci.yml`, `docs/engineering/EXCEPTION_CONVENTIONS.md`, `docs/security/SECURITY_CHECKLIST.md`,
`03-basic-design/*/README.md`, `docs/engineering/LESSONS.md`.
