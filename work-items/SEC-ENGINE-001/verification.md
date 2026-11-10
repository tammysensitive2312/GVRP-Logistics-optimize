# Verification — SEC-ENGINE-001

Verifier: Claude Code, 2026-11-10. Version: worktree `E:\gvrp-wt\sec-engine-001`, branch
`fix/sec-engine-001`, uncommitted changes on top of `bdbaf29` (listed in `implementation.md`).
Environment: Windows 11, JDK 21, Maven offline; MySQL in Docker (`sembackend-db-1`); Engine and Entry
run from the worktree with the git-ignored local property files; Engine GraphHopper data from the main
checkout; result spool in the worktree.

## Automated

| Check | Command | Outcome |
|---|---|---|
| New Engine tests | `mvnw.cmd -o -Dtest=ServiceKeyFilterTest,ServiceKeyFilterMockMvcTest,OptimizationControllerLimitsTest,SolverDeadlineTest,OptimizationControllerProgressTest test` | 18/18 pass |
| Engine suite, CI style | `SPRING_PROFILES_ACTIVE=ci mvnw.cmd -o verify` with the CI exclusion list | 142 tests, 0 failures (126 before + 16 new) |
| New Entry test | `mvnw.cmd -o -Dtest=EngineApiClientKeyTest test` | 3/3 pass |
| Entry suite, CI style | `SPRING_PROFILES_ACTIVE=ci mvnw.cmd -o verify` with the CI exclusion list | 347 tests, 0 failures (344 before + 3 new) |
| Secret check | `node tools/check-secrets.mjs` | OK |
| Key leakage | new key value searched (count only) in both run logs, both CI logs, tracked and untracked files of the worktree | 0 occurrences everywhere |

## Runtime (local stack, 2026-11-10 ~23:00 +07:00)

Direct calls to the Engine on port 8081 from a shell:

| Request | No key | Wrong key | Correct key |
|---|---|---|---|
| `GET /api/v1/optimization/health` | 200 | — | — |
| `POST /api/v1/optimization` | 401 | — | 202 / 400 (see limits) |
| `GET /{id}/progress`, `POST /{id}/cancel`, `GET /{id}/result` (unknown id) | 401 each | — | 404 each (filter passed, handler answered) |
| `POST /{id}/resend`, `POST /evaluate` | 401 each | 401 (evaluate) | — |
| `POST /legs` with `{}` | 401 | — | 400 (handler validation) |
| `GET /999/%70rogress`, `GET /health/`, `GET /%68ealth`, `POST /health` | 401 each | — | — |
| `GET /actuator`, `GET /error` | 401 each | — | — |

Limits with the correct key: `max_iterations=100000000` → 400 "max_iterations must be between 1 and
20000"; `num_threads=64` → 400; `max_iterations=0` → 400. The Engine log shows no job started for the
probe job id (0 "Starting optimization" lines).

End-to-end through the UI (user signed in as planner of branch 1000, "Branch Huge 1"): 1 order
(`LEZT-01`, 29/10/2026) × 1 vehicle, goal MINIMIZE_COST, speed NORMAL → job #96.

- Entry → Engine submit: 202 ACCEPTED (key sent and accepted).
- Engine: 2000 iterations, 4 threads (unchanged preset); log line "Runtime cap: 7200s …; request
  timeout 900s is advisory"; solve finished in < 1 s; runtime cap not reached.
- First completion callback → Entry 503 "Job #96 còn PENDING" (pre-existing early-callback guard:
  the one-order solve finished before Entry marked the job PROCESSING); Engine retry 2 → 200 OK; job
  96 COMPLETED, solution 78.
- Solution 78 is INFEASIBLE with 0 served: "None of the 1 vehicles in this request has all required
  skills: [electric]" — the order needs an electric vehicle and a diesel truck was chosen. Expected
  business behaviour, unrelated to this change.

Data created in the local DB by this check: job 96 and solution 78 (branch 1000).

## Requirement coverage

| Req | Evidence | Status |
|---|---|---|
| EN-R1 | EN-T1…T3, EN-T5; runtime table | VERIFIED |
| EN-R2 | EN-T4; startup ERROR line observed in tests | VERIFIED (unit); not exercised at runtime |
| EN-R3 | `MessageDigest.isEqual` (code review); placeholders only in tracked files; `check-secrets`; leak search | VERIFIED |
| EN-R4 | EN-T9; job 96 accepted by the Engine | VERIFIED |
| EN-R5 | EN-T6; runtime limit probes | VERIFIED |
| EN-R6 | EN-T7 | VERIFIED (unit); not exercised at runtime |
| EN-R7 | EN-T8; runtime log of the cap | VERIFIED (unit); the cap was not reached at runtime |
| EN-R8 | EN-T6 presets; full suites green; job 96 used 2000 it / 4 threads | VERIFIED for the preset path; solver results of large runs not re-measured |

## Security review (AGENTS.md §15 red flags)

- Hard-coded secrets: none (tests use dummy repeated-character keys).
- TLS or hostname checks: untouched.
- Swallowed exceptions: none added; Engine 401 on evaluate/legs is now surfaced as
  `EngineUnavailableException` instead of being reported as bad input.
- New unauthenticated path: only `GET /api/v1/optimization/health` (returns status, service name and
  time; needed for liveness checks). Exact-match, fail closed.
- New endpoints, queries on branch data, dependencies, test modifications: none.
- The `/security-review` skill was not run: it reviews the current checkout's pending changes and this
  session's working directory is the main checkout, not the worktree. This list is a manual review.

## Limitations

- D5 executor concurrency: the `@Async` job thread was named `task-1`. Both a pooled executor and a
  virtual-thread `SimpleAsyncTaskExecutor` use that prefix, so whether concurrent jobs are bounded is
  NOT_VERIFIED.
- The runtime cap covers the Jsprit search only, not the distance-matrix build.
- No body-size limit before JSON parsing; only authenticated callers (Entry) can send a body now.
- Network exposure of port 8081 (deployment) is unchanged; the key makes it non-exploitable without
  the key, it does not hide the port.
