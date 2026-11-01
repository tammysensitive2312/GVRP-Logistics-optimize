# Context — SEC-AUDIT-001

Inspected 2026-11-01 by Claude Code on `master` @ `c0cdfd5` (clean worktree). Facts below are starting points for reconnaissance, not conclusions.

## The skill

- Installed at `.agents/skills/security-audit/` from `https://github.com/cloudflare/security-audit-skill` commit `c1c8a8c1471069fb0e188eeaff69b8e8db6564a8` (MIT, `LICENSE` copied beside `SKILL.md`). Files are byte-identical to that commit; Claude read `README.md`, `SKILL.md` and scanned both validators: they use only `fs`, `path`, `util` and only read their input file (no network, no child process, no writes).
- `.agents` is git-ignored (`.gitignore` line 87): the skill is a local install on this machine and is not committed. Its version is pinned by the commit hash above and by the workflow run scope (file hashes recorded at `init`).
- Entry point `SKILL.md`; phases in `RECONNAISSANCE.md`, `HUNTING.md`, `ATTACK-CLASSES.md`, `VALIDATION-AND-REPORTING.md`; companion domains used here: `DATA-ISOLATION-AND-LIFECYCLE.md`, `WEB-PROTOCOL-AND-AUTH.md`, `CLIENT-SIDE.md`, `SUPPLY-CHAIN-AND-RELEASE.md`, `PROTOCOLS-RPC-AND-MESSAGING.md`, `RESOURCE-EXHAUSTION-AND-AVAILABILITY.md`.
- **Windows trap:** both validators refuse every input on Windows (`OS no-follow and nonblocking input protection is unavailable`): Node on Windows has no `fs.constants.O_NOFOLLOW` / `O_NONBLOCK`. Running them under Windows Node gives 22 pass / 7 fail in `validate-findings.test.cjs`. In a Linux container they work: `node:22-alpine` (image `ae59e9992c6b`, pulled 2026-11-01 with the user's approval) gives 34/34 and 31/31 test passes with `--network none --read-only --tmpfs /tmp`.
- The skill requires an OS-enforced sandbox to execute target code. This Windows host has none, so the run is source-only (request SA-R3).
- The skill's parent must delegate to isolated sub-agents. In this repository's OpenCode setup only `gvrp-orchestrator` is `mode: primary`; `gvrp-engineer` and the others are subagents. OpenCode's built-in `explore` (read-only) and `general` agents map to the skill's `research` and `general` roles.

## System under audit (scope)

- `GVRP_Entry_API` (Spring Boot, port 8080): 18 controllers in `controller/` (Auth, Base, BranchZone, Depot, Driver, Fleet, LezCompliance, LezMembership, OptimizationCallback, OptimizationJob, Order, Pareto, RouteSend, Solution, Vehicle, VehiclePermit, VehicleType, ZoneTemplate). `config/SecurityConfig.java`: `permitAll` on `/api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`, `/api/v1/solutions/callbacks/**`; `/api/v1/driver/**` → `DRIVER`; `/api/v1/**` → `PLANNER`. Filters: `security/jwt/JwtAuthenticationFilter.java`, `security/ApiKeyAuthenticationFilter.java` (engine callbacks). Branch comes from the JWT principal; repositories use `...AndBranchId` lookups (see SEC-002/003 actions in the checklist). File import parsers in `service/integration/file/` (CSV, JSON, text). GeoJSON parsing in `service/lez/GeoJsonGeometryParser.java`.
- `GVRP_Engine_API` (port 8081): `controller/OptimizationController.java` under `/api/v1/optimization` — `/{jobId}/progress`, `/{jobId}/cancel`, `/{jobId}/result`, `/{jobId}/resend`, `/health`, `/evaluate`, `/legs`, plus job submission. No request authentication or rate limit (SEC-010). Calls back to Entry with an API key.
- `GVRP_Entry_client_v2` (Angular 18): token storage and session state in `src/app/core/services/auth.service.ts`, `auth.interceptor.ts`, the new `session-reset.service.ts` (SEC-011 fix, `c0cdfd5`), stores in `src/app/core/services/`.
- CI: `.github/workflows/ci.yml`; Dependabot configuration under `.github/`.
- Data: MySQL `gvrp_db` (Docker container `sembackend-db-1`). Branch-owned tables carry `branch_id`; solution graph `optimization_jobs → solutions → routes → route_stops`, plus `route_sends`, `pareto_candidates`, `unassigned_orders`, LEZ tables `branch_zones`, `branch_zone_rules`, `vehicle_permits`. Schema reference: `GVRP_Entry_API/src/main/resources/database/` (`GVRP_master_initial.sql` + `migrations/`).

## Prior evidence (must be fed to reconnaissance)

`docs/security/SECURITY_CHECKLIST.md` §1, status on 2026-11-01:

| ID | Severity | Status |
|---|---|---|
| SEC-001 | Critical | OPEN — credentials committed in `application.properties` (names only), rotation deferred |
| SEC-002 | High | FIXED on branch `fix/sec-tenant-001` — solution IDOR |
| SEC-003 | High | FIXED on branch `fix/sec-tenant-001` — foreign IDs in vehicle/fleet writes |
| SEC-004 | Low | OPEN — callback API key compared with `String.equals` |
| SEC-005 | Low | OPEN — test-account credentials in a tracked work item |
| SEC-006 | Medium | FIXED on branch `fix/sec-role-001` — DRIVER tokens on planner endpoints |
| SEC-007 | Low | OPEN — global license-plate uniqueness reveals other branches |
| SEC-008 | Low | OPEN — public register endpoint fakes success |
| SEC-009 | Medium | FIXED on branch `fix/sec-session-001` — disabled users keep API access |
| SEC-010 | Medium | OPEN, accepted until Gate B — Engine API unauthenticated, no rate limit |
| SEC-011 | High | FIXED on `master` 2026-11-01 (`c0cdfd5`) — client stores outlived logout |

"FIXED on branch X" says where the fix was made, not that it is on `master`. Whether each fix is present in the audited source is part of SA-R5.

There is no prior skill run (no earlier `coverage-ledger.json` / `findings.json`); state this in the coverage statement.

## Lessons that apply

- L-2026-006 — workflow commands are run by the engineer or one closing script.
- L-2026-009 — normalize line endings before byte comparison.
- L-2026-012 — verification runs mutate the local database (reason for the no-DB-write rule).
- L-2026-013 — `recover` shows no scope changes without a checkpoint (record a checkpoint if the run is interrupted).
- L-2026-014 — workflow and git from the right shell (run from the repository root on Windows).

## Out-of-scope observations

None yet. New findings go to `docs/security/SECURITY_CHECKLIST.md` at closure, written by Claude, not by the auditor.
