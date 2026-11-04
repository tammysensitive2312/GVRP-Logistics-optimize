# Implementation — SEC-CALLBACK-001

Date: 2026-11-04. Engineer: Claude Code (side session), worktree `E:\gvrp-wt\sec-callback-001`,
branch `fix/sec-callback-001` cut from `master` `f095ef4`. Nothing committed.

## Scope

Fix SEC-012 (callback API key skipped on encoded callback paths) and SEC-013
(completion callback writes orders/vehicles of other branches) per `design.md`
D1–D4. Paths below are relative to `GVRP_Entry_API/src/`.

## Baseline (before any change)

`.\mvnw.cmd -B clean test` (JDK 21) — exit 1. Tests run: 331, Failures: 0, Errors: 3.
Pre-existing, environment-related errors (recorded, not fixed):

- `DepotMapperTest.testToDTO`
- `DepotMapperTest.testMapperBeanIsCreated`
- `GvrpEntryApiApplicationTests.contextLoads`

All three are `@SpringBootTest`; context fails creating `jwtTokenProvider`
(`WeakKeyException`: JWT secret resolves to 0 bytes in this worktree — the
gitignored local secret configuration is absent here). Both classes are in the
CI exclusion list (`.github/workflows/ci.yml`, `-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest`).

## Changes

| Req | File | Change |
|---|---|---|
| CB-R1, CB-R2 (D1) | `main/.../config/SecurityConfig.java` | `/api/v1/solutions/callbacks/**` changed from `permitAll()` to `hasRole("ENGINE_SERVICE")`, before the `/api/v1/**` PLANNER rule. New `FilterRegistrationBean<ApiKeyAuthenticationFilter>` with `setEnabled(false)` so the `@Component` filter runs only inside the security chain (no existing registration pattern for `JwtAuthenticationFilter` to follow). |
| CB-R1, CB-R3 (D1, D2) | `main/.../security/ApiKeyAuthenticationFilter.java` | Removed the raw `getRequestURI().startsWith(...)` gate and the `CALLBACK_PATH_PREFIX` constant. No header → pass through (authorization rule answers 401 via `JwtAuthenticationEntryPoint`); invalid or empty header → 401 as before; valid → `ROLE_ENGINE_SERVICE`. Key compared with `MessageDigest.isEqual` on UTF-8 bytes; an empty configured key never matches. Logs never contain the key; the invalid-key warning logs only the servlet path. |
| CB-R4, CB-R5 (D3) | `main/.../service/OptimizationCallbackService.java` | `saveSolution`: vehicle lookup (`vehicleRepository.findAllById`, still only when routes are present) moved up next to the order lookup; new `requireSameBranch(...)` runs right after both lookups and before any `bulkTransition`, materialisation, Pareto save or solution save. Refuses when an order id is missing or its branch ≠ job branch, or a vehicle id is missing or `fleet.branch` ≠ job branch. Logs job id and counts only. `handleCompletion`: new `catch (CallbackRejectedException)` that rethrows before the catch-all, so the job is not marked FAILED. Existing `findAllById` calls kept. |
| D3 | `main/.../exception/CallbackRejectedException.java` (new) | Unchecked exception. |
| D3 | `main/.../exception/GlobalExceptionHandler.java` | One handler: `CallbackRejectedException` → 422, code `VALIDATION_ERROR` (`0040001`). |
| D4 | `test/.../security/CallbackAuthenticationTest.java` (new) | CB-T1..CB-T5, `@WebMvcTest` through the real `SecurityConfig`, DB-free. |
| D4 | `test/.../service/OptimizationCallbackBranchScopeTest.java` (new) | CB-T6..CB-T9, Mockito, DB-free. |

## Modified Tests

Approved by the user, relayed by the parent session on 2026-11-04 with the
user's words: "Làm theo phương án a" (option A in `blocker-vehicle-fixtures.md`:
fixture-only change, vehicle gets a fleet in the job's branch; no assertion,
stub, name or other fixture changed).

| File | Line | Change | Reason |
|---|---|---|---|
| `test/.../service/OptimizationCallbackServiceLezTest.java` | 110 (`setUp`), import line 15 | `Vehicle.builder().id(VEHICLE)` + `.fleet(Fleet.builder().branch(branch).build())` | D3 vehicle-branch check needs `vehicle.fleet.branch`; production columns are `nullable = false`. |
| `test/.../service/OptimizationCallbackServiceParetoTest.java` | 152, import line 15 | same, inside the `vehicleRepository.findAllById` stub return value | same |
| `test/.../service/OptimizationCallbackUnassignedOnceTest.java` | 76, import line 14 | same | same |

No test was deleted, renamed, disabled, focused or excluded.

## Commands (exit codes)

| Command (in `GVRP_Entry_API`, `JAVA_HOME=C:\Program Files\Java\jdk-21`) | Exit | Result |
|---|---|---|
| `.\mvnw.cmd -B clean test` (baseline) | 1 | 331 run, 0 failures, 3 errors (pre-existing above) |
| `.\mvnw.cmd -B -q test -Dtest=CallbackAuthenticationTest,OptimizationCallbackBranchScopeTest,OptimizationCallback*Test,RoleAccessTest` | 0 | new 7 + 6 pass; existing callback service tests 5 + 3 + 1, role tests 11/10/9/4 pass |
| Regression proof: `SecurityConfig.java` and `ApiKeyAuthenticationFilter.java` temporarily restored to `HEAD`, `.\mvnw.cmd -B -q test -Dtest=CallbackAuthenticationTest`; then new files restored | 1 | 3 of 7 fail on old code (`cbT2` encoded no-key, `cbT4` encoded wrong key: expected 401 but was 200, `cbT5`) — the tests detect SEC-012 |
| `.\mvnw.cmd -B clean test` (after) | 1 | 344 run (= 331 + 13 new), 0 failures, same 3 pre-existing errors only |
| `.\mvnw.cmd -B clean test -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"` (CI selection) | 0 | 341 run, 0 failures, 0 errors, BUILD SUCCESS |

Logs (gitignored `*.log`): `GVRP_Entry_API/baseline-test.log`, `after-test.log`, `after-ci-test.log`.

## Verification

- CB-T1 plain path, no key → 401, handler not called.
- CB-T2 `%63allbacks`, `%73olutions`, trailing slash, `//` without key → each 400 or 401, handler never called.
- CB-T3 valid key on plain and `%63allbacks` path → 200, handler called once.
- CB-T4 wrong key (plain and encoded) and empty key → 401.
- CB-T5 `@WithMockUser(roles="PLANNER")` on plain and encoded path → 401/403, handler not called.
- CB-T6 foreign unassigned order; foreign served order → `CallbackRejectedException`, no `bulkTransition`, no materialiser, no solution or job save, job stays `PROCESSING`, `jobStatusUpdater` untouched.
- CB-T7 foreign-branch vehicle; unknown vehicle → rejected, nothing written.
- CB-T8 unknown order id → rejected, nothing written.
- CB-T9 same branch → solution saved, `ON_ROUTE` transition, job `COMPLETED`.
- Runtime (running servers) NOT verified by the engineer: the user's Entry API on 8080 runs the old build and was not restarted (task constraint). See Handoff.

## Security-Relevant Changes

- **Removed `permitAll`**: `/api/v1/solutions/callbacks/**` is no longer public; it requires `ROLE_ENGINE_SERVICE`, granted only by a valid `X-API-Key`. No new `permitAll`.
- The API key filter now runs on every path inside the security chain. Effect outside callbacks: a request carrying a *wrong* `X-API-Key` header now gets 401 on any path (before: ignored outside the raw callback prefix). A valid key grants only `ROLE_ENGINE_SERVICE`, which no other rule accepts.
- Constant-time key comparison (closes the code part of SEC-004 for the Entry side).
- Completion callbacks are refused (422) when any order/vehicle is unknown or outside the job's branch, before any write; the job is not marked FAILED.
- No secret printed or written; no dependency added; `pom.xml` and properties untouched.

## Deviations

- Design D3 vehicle check required changing three existing test fixtures; done only after the user's approval (see Modified Tests).
- Refusal code: `ErrorCode.VALIDATION_ERROR` with HTTP 422 (no dedicated code added, `util/ErrorCode.java` is outside the allowed files).
- `JwtAuthenticationFilter` is also a `@Component` filter registered in the plain servlet chain; left unchanged (out of scope, design only asked for the API key filter).

## Remaining Uncertainty

- Engine reaction to 422 on a completion callback: Not verified. If the engine outbox retries 4xx, a rejected payload would be retried; it can never succeed, which is intended for forged payloads, but a genuine engine payload with a deleted order would now be refused instead of being stored (previously such ids were silently dropped by `findAllById`).
- A completion where routes contain a `null` vehicle id is not refused by the branch check (same as before: materialisation decides).
- MockMvc decoding of encoded paths approximates Tomcat; the runtime re-probe below is the authoritative check.

## Lesson Candidates

- When a design adds a check on a lazily related entity (`vehicle.fleet.branch`), grep the existing Mockito fixtures for that entity first; builder fixtures often omit `nullable = false` relations, and the §14 approval round-trip costs a session turn.

## Handoff

Version: working tree of `fix/sec-callback-001` (uncommitted) on top of `f095ef4`.
Before re-probing, build and start the Entry API from this worktree (or after
merging) instead of the currently running one. Keep the API key in an
environment variable; never paste it.

1. Encoded callback path without key must now be 401 (was 200):

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" -X POST "http://localhost:8080/api/v1/solutions/%63allbacks/progress" -H "Content-Type: application/json" -d '{\"job_id\":999999999,\"progress\":1,\"current_phase\":\"OPTIMIZING\"}'
curl.exe -s -o NUL -w "%{http_code}`n" -X POST "http://localhost:8080/api/v1/%73olutions/callbacks/progress" -H "Content-Type: application/json" -d '{\"job_id\":999999999,\"progress\":1,\"current_phase\":\"OPTIMIZING\"}'
```

Expected: `401` both. The no-key plain path `/api/v1/solutions/callbacks/progress` also 401.

2. Forged foreign-branch completion must be refused and change nothing.
Precondition (read-only, e.g. dbhub): job 70 is branch 1000, `FAILED`, no solution; order 1 is branch 1, `SCHEDULED`
(restore SQL in `work-items/SEC-AUDIT-001/runtime-validation.md` if the audit probe left it changed).

```sql
SELECT id, branch_id, status FROM optimization_jobs WHERE id = 70;
SELECT id, branch_id, status FROM orders WHERE id = 1;
SELECT COUNT(*) FROM solutions WHERE job_id = 70;
```

a) Without key (encoded path) — expected `401`:

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" -X POST "http://localhost:8080/api/v1/solutions/%63allbacks/complete" -H "Content-Type: application/json" -d '{\"job_id\":70,\"solution\":{\"routes\":[],\"unassigned_orders\":[{\"order_id\":1,\"reason\":\"probe\"}],\"served_orders\":0,\"unserved_orders\":1}}'
```

b) With the real engine key from your environment (`$env:ENGINE_API_KEY` set by you to the `engine.api.key` value) — expected `422`, body code `0040001`:

```powershell
curl.exe -s -w "`n%{http_code}`n" -X POST "http://localhost:8080/api/v1/solutions/callbacks/complete" -H "Content-Type: application/json" -H "X-API-Key: $env:ENGINE_API_KEY" -d '{\"job_id\":70,\"solution\":{\"routes\":[],\"unassigned_orders\":[{\"order_id\":1,\"reason\":\"probe\"}],\"served_orders\":0,\"unserved_orders\":1}}'
```

Then re-run the three SELECTs: job 70 still `FAILED`, order 1 still `SCHEDULED`, solution count still 0.
The body uses the `CompletionCallback` DTO fields in snake_case (`job_id` and
`solution` are `@NotNull`; `unassigned_orders[].order_id`). A 400 would mean the
body did not bind; adjust it to reach the 422 path for a meaningful check.

Known limitations: runtime behaviour not verified by the engineer; engine 4xx retry behaviour not verified.
