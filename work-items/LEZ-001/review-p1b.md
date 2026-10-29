# Review — LEZ-001 P1b (independent)

Reviewer: Claude (claude.ai session), 2026-10-27 +07:00. Static reading through the file bridge; nothing was executed, no database was touched.
Subject: `implementation.md` §P1b (OpenCode session), branch `feature/lez-001`, uncommitted tree.
Status words follow AGENTS.md §5.

## 1. Verdict

**CHANGES REQUIRED before P1c.** One HIGH defect in the send guard (§3, F1) turns every refused send into a long hang followed by a 500, and loses the verdict it was meant to keep. The rest of P1b is sound and matches design §5 / §16; the engineer's record is honest about what was not exercised.

## 2. What was inspected

| Area | Files |
|---|---|
| Send guard | `service/RouteSendService.java` (`sendRoute`, `enforceLezCompliance`, `fillMissingLegs`, `sendDetail`) |
| Certification | `service/lez/RouteComplianceService.java`, `ComplianceChecker.java`, `RestrictionCompiler.java` |
| Engine | `service/LegsService.java`, `controller/OptimizationController` (`/legs`) |
| Schema | `database/migrations/2026-10-28-lez-compliance.sql` |
| Tests | file list of both modules; `RouteSendServiceLezTest` |
| Record | `implementation.md` §P1b, `checkpoint-p1b.json` |
| Security | `docs/security/SECURITY_CHECKLIST.md` (SEC-001…009) |

**Test integrity.** The test trees contain only new LEZ files beside the historical ones (Engine: `PolylineEncoderTest`, `LegGeometryServiceTest`, `LegsEndpointTest`, `LezSkillMappingTest`, `LezSkillAssignmentTest`; Entry: the eight classes in the §P1b table plus `LezFixtures`). Timestamps of the historical test files predate LEZ-001. The D6 exclusions exist only on the command line, as decided. No sign of a weakened or skipped existing test.

Reported results (not re-run by the reviewer): Entry 240/0/0/0 (two `@SpringBootTest` classes excluded because the P1b migration is not applied, same as P1a); Engine 162/0/0/0 with the D6 exclusions.

## 3. Findings

| ID | Severity | Finding | Required action |
|---|---|---|---|
| F1 | **HIGH** — fix task `task-opencode-p1b-fix.md` | **Self-blocking lock on the refusal path.** `sendRoute` (`@Transactional`) takes `routeRepository.findWithLockById` (PESSIMISTIC_WRITE → `SELECT … FOR UPDATE`). On a violation, `enforceLezCompliance` calls `RouteComplianceService.persistViolation`, which is `@Transactional(propagation = REQUIRES_NEW)` and runs `update Route r … where r.id = :id` on **the same row** from a second connection. The outer transaction holds the row lock and waits for the inner call; the inner one waits for the lock. InnoDB does not see a deadlock (two connections, one thread), so it waits `innodb_lock_wait_timeout` (default 50 s), then throws a lock-timeout exception. Result: the planner waits ~50 s, gets a 500 instead of the 422, and the VIOLATION verdict is **not** stored. The unit test mocks `EntityManager`, so it cannot see this; `implementation.md` already lists it as unverified. | Recommended fix: drop `REQUIRES_NEW`. Put `@Transactional(noRollbackFor = UnprocessablePlanException.class)` on `sendRoute`, keep the verdict on the managed `route` (`certify` already sets it) and let the 422 commit it together with the filled legs. Nothing else has changed at that point (the supersede/insert block runs after the guard). Delete `persistViolation` or make it a plain method. Alternative: certify before taking the lock — more invasive. Verify manually (§5 step 4): a violating send must answer 422 within seconds and the route must show `lez_status = VIOLATION`. |
| F2 | MEDIUM | **Fail-open wiring.** `RouteComplianceService` and `EngineApiClient` are `@Autowired(required = false)` fields (Deviation 4). If either bean is missing at runtime, the send guard silently returns and routes go out unchecked. Deviation 4's sentence "never for the send guard's legal check" is wrong: the code returns early in that case too (the Remaining Uncertainty paragraph says so correctly). | Add a startup check (a small `@Component` with `@PostConstruct` or an `ApplicationRunner`) that fails boot when either bean is absent outside tests. Does not touch historical tests. Correct the Deviation 4 sentence. |
| F3 | LOW | **Checker skips a missing restriction.** In `ComplianceChecker`, a `(vehicle, zone)` pair without a `VehicleRestriction` is `continue`d. The compiler emits a row for every solution vehicle × enabled zone, so this only happens when the route has no vehicle (`vehicleId == null`), but then the stop is not checked at all. | Fail closed: a route in a zone with no restriction row for its vehicle → `LEZ_DATA_MISSING`. One unit test. |
| F4 | LOW | **Silent skip when no date.** `enforceLezCompliance` returns when `currentFor` is null, i.e. when no order of the solution has a delivery date. | Confirm `delivery_date` is NOT NULL in the schema; if it is nullable, refuse with 422 `LEZ_DATA_MISSING`. |
| F5 | LOW | **Wrong documentation of time semantics.** The migration comment and `implementation.md` (Remaining Uncertainty "Time semantics") say compliance minutes are "minutes from 08:00". The checker uses minutes of the day from the stored `LocalTime` values, the same scale as the rules. Code is right; the text is wrong and will mislead P2. Also the migration comment's example `LEZ_UNREACHABLE` is a code P1b does not emit. | Fix both comments and the paragraph. |
| F6 | LOW | **`LegsService` javadoc** says `vi_VN` is registered. The engineer's own probe shows `vi_VN` falls back to English; `vi`/`vi-VN` are the working tags. | Fix the javadoc. |
| F7 | INFO | **Engine call inside the lock.** `fillMissingLegs` calls the engine while the route row is locked and the DB connection is held. With F1 fixed this is acceptable for one route; note it for P2 (batch send). Every send of a pre-P1b route now depends on engine uptime (fails closed with 502, as designed). | None now. |
| F8 | INFO | **D4 breaks multi-date clients.** Job submit is now 400 for mixed dates in every branch. The Angular client was not changed. | Handle in P1c (client message and date picker). |

## 4. Deviations — reviewer decisions

| Deviation | Decision |
|---|---|
| 1 (D6 exclusions) | Accepted — user decision. |
| 2 (hash scope = solution-scoped current compile) | **Accepted.** Comparing like with like on read is the right choice; record in design §16 at P1c. |
| 3 (snake_case on the wire) | Accepted. |
| 4 (optional field injection) | Accepted only with F2's startup check. |
| 5, 6, 8, 9, 11, 12 | Accepted. |
| 7 (`REQUIRES_NEW` persistence) | **Rejected** — see F1. |
| 10 (`/legs` 200 with per-leg `ROUTE_NOT_FOUND`) | Accepted; the guard fails closed through `LEZ_GEOMETRY_MISSING`. |
| 13 (coordinate-safe logs, also in the pre-existing `fetch()`) | Accepted; same defect class, same file, recorded openly. |
| 14 (empty snapshot for zone-free branches) | Accepted. |

## 5. Manual verification for the user (after F1 is fixed)

1. Apply `2026-10-28-lez-compliance.sql` **after** `2026-10-27-lez-zones.sql`.
2. `GVRP_Entry_API`: `mvnw.cmd -B clean test` → expected **243/0/0/0** (240 + the two `@SpringBootTest` classes, 3 tests).
3. Compliant send: a route outside every enabled zone → 200; the driver detail has `geometry_to_next` and Vietnamese `instructions_to_next`.
4. **Refused send:** enable a zone that bans the route's vehicle all day and covers one stop → **422 within a few seconds** (not ~50 s, not 500), message `ZONE_ACCESS seq=…`; `GET /api/v1/solutions/{id}/compliance` shows that route `VIOLATION`.
5. `POST /api/v1/solutions/{id}/compliance/recheck`, then change a rule and `GET …/compliance` → `STALE`.
6. `GET /api/v1/routes/{id}/geometry` → one leg per stop pair; another branch's route id → 404.

## 6. Security

Added to `docs/security/SECURITY_CHECKLIST.md` as SEC-010 by the reviewer on the user's instruction (2026-10-27 23:22). Summary:

> **SEC-010 — Engine API has no request authentication (Medium, OPEN).** `GVRP_Engine_API` exposes `/api/v1/optimization/**` (including the new synchronous `/legs`, up to 1000 routing queries per request) with no authentication and no rate limit. Anyone who can reach the port can consume routing and solver capacity. SEC-004 covers only the callback key comparison. Accepted posture today: the engine must be reachable only from the Entry API host (firewall / private network). Remediation: a shared service key filter (as for the callback) or mTLS, plus a request-size and rate limit on `/legs`.

Other security points checked: no new `permitAll`; tenant scope from the principal on all three new Entry endpoints (404 for another branch); the driver payload carries zone names only; no coordinates in new logs.

## 7. Review of the P1b fix (2026-10-28)

Subject: `implementation.md` §P1b fix, task `task-opencode-p1b-fix.md`. Static reading; nothing executed.

**Verdict: F1 FIXED in code — accepted, pending the user's manual check (§5 step 4).**

| Check | Result |
|---|---|
| `sendRoute` | `@Transactional(noRollbackFor = UnprocessablePlanException.class)`. The guard still runs after the role, tenant, solution-status and driver checks (those throw `DataInvalidException`/`ForbiddenException` and still roll back) and before the supersede/insert block. |
| `enforceLezCompliance` | No second transaction; `certify` writes the verdict on the locked, managed route; the 422 message is unchanged. `LEZ_MIXED_DATES` is thrown before any entity change. |
| `RouteComplianceService` | `persistViolation`, `EntityManager`, `REQUIRES_NEW` removed; no `@Transactional` left in the class. |
| Callers | `RouteSendController.sendRoute` has no transaction of its own, so the service boundary decides commit/rollback. |
| Exception hierarchy | Engineer reports 0 subclasses of `UnprocessablePlanException`; pinned by `RouteSendTransactionTest.failuresAfterTheGuardStillRollBack`. |
| Tests | `RouteSendTransactionTest` (3, reflection) added. In `RouteSendServiceLezTest` only SG-T2's two `EntityManager` verifications were replaced by entity-state assertions, and the hash assertion was **strengthened** (equals the compiled hash). SG-T1, T3, T4 unchanged. Matches the task; recorded under Modified Tests. |
| Scope | Only the four allowed source/test files plus the record and checkpoint. F2–F8 untouched, as instructed. |
| Results reported | Baseline 240/0/0/0; final 243/0/0/0 with the two schema-dependent classes still excluded (P1b migration not applied; engineer confirmed by a read-only `information_schema` look). |

Corrections to the engineer's record:
- The P1a migration `2026-10-27-lez-zones.sql` is already applied (P1a acceptance, 2026-10-27 08:25). Only `2026-10-28-lez-compliance.sql` is pending.
- After that migration the full run should be **246** (243 + the 3 tests of `DepotMapperTest` (2) and `GvrpEntryApiApplicationTests` (1)), not 245.
- Residual edge (accepted): if the commit of the verdict itself fails (for example the migration is missing), the client gets a 500 from the commit instead of the 422. Applying the migration before running the app removes this case.

Still open: F2–F8; real lock behaviour NOT_VERIFIED until §5 step 4.

## 8. DRIVER-APP-001

Not reviewed: the worktree holds only the `flutter create` scaffold and `pubspec` (22:45). No `lib/` feature code, no tests from DT-01…DT-13, no `implementation.md`. Nothing to review yet.
