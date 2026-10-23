# Engineering Lessons

How to work in this repository without repeating past mistakes: build, test,
CI, tooling, workflow and delegation traps.

Scope boundary:

* Defects and scientific hazards of the GVRP system itself go to
  `docs/research/KNOWN_ISSUES.md`.
* Security findings go to `docs/security/SECURITY_CHECKLIST.md`.
* Facts true only on one machine (paths, JDK location, local ports in use) go
  to the user-level agent file (`~/.config/opencode/AGENTS.md`), not here.

Rules (AGENTS.md section 16):

* Add an entry when a work item hit a non-obvious trap that the next agent
  would hit again. Do not add what the code or a command already shows.
* Every entry has a date and a source (work item, file or user message).
* Never delete an entry. When it no longer applies, set
  `Status: SUPERSEDED by L-YYYY-NNN` or `Status: OBSOLETE — <reason, date>`.
* Next free ID: take the highest existing number plus one.

Entry format:

```text
## L-YYYY-NNN — <short title>
Date: YYYY-MM-DD
Source: <work item / file / user message>
Applies to: <modules, commands or situations>
Status: ACTIVE
Lesson: <what to do or avoid, and why>
```

---

## L-2026-001 — CI has no MySQL and no GraphHopper graph
Date: 2026-10-16
Source: `.github/workflows/ci.yml`; work-items/SEC-TENANT-001, SEC-ROLE-001
Applies to: every new Java test
Status: ACTIVE
Lesson: New tests must run without a database or the `.data/*.osm.pbf` graph
(Mockito, `@WebMvcTest`, plain unit tests). `@SpringBootTest` classes are
excluded by name in `ci.yml`; adding a class to that list is a test-exclusion
change and needs user approval (AGENTS.md section 14). If a DB-free test is
impossible, stop and report instead of excluding it.

## L-2026-002 — Spring Data turns a null parameter into IS NULL
Date: 2026-10-16
Source: work-items/SEC-TENANT-001/closure.md
Applies to: derived queries such as `findByIdAndBranchId` used for tenant scoping
Status: ACTIVE
Lesson: A null branch argument matches rows whose branch column is NULL. Such a
query is fail-closed only when the column is NOT NULL (true for
`depots.branch_id`). For a nullable column, reject a null branch explicitly
before querying.

## L-2026-003 — Report a clean build, not an incremental one
Date: 2026-10-16
Source: work-items/SEC-TENANT-001/closure.md ("no clean build" gap)
Applies to: Maven verification in Entry API and Engine API
Status: ACTIVE
Lesson: Classes compiled earlier by the IDE in `target/` can hide compile or
test failures. Verification evidence must come from `mvnw.cmd -B clean test`.

## L-2026-004 — Check test libraries before specifying a test
Date: 2026-10-16
Source: work-items/SEC-ROLE-001/closure.md
Applies to: task specs for security or web-layer tests
Status: ACTIVE
Lesson: `spring-security-test` was missing from `GVRP_Entry_API/pom.xml`; the
spec had to allow exactly that dependency. Read the pom before writing the test
table. On Spring Boot 3.4+ use `@MockitoBean`; `@MockBean` is deprecated
(follow-up open in `RoleAccessTest`).

## L-2026-005 — Freeze run scope between `pass` and `finish`
Date: 2026-10-16
Source: Claude session 2026-10-16 (an edit to hashed `traceability.md` had to be reverted); `tools/feature-workflow/workflow.mjs`
Applies to: business-development workflow runs
Status: ACTIVE
Lesson: `accepted` and `finish` re-check the handoff hash, and evidence files
are hashed when a transition records them. Editing a scoped file or recorded
evidence after `pass` breaks the run. Put later facts in `closure.md` and make
post-closure edits after `finish`.

## L-2026-006 — Workflow commands are run by the engineer or one closing script
Date: 2026-10-16
Source: work-items/SEC-ROLE-001/closure.md (friction), SEC-SESSION-001/closure.md
Applies to: Claude → OpenCode delegation (`/delegate`)
Status: ACTIVE
Lesson: Claude may have file tools but no shell on the user's machine. Relaying
individual commands through the user was the main friction in the first trial.
The engineer runs Steps 0–4 including workflow commands; closing is one
`close-run.ps1`.

## L-2026-007 — Check JAVA_HOME before running Maven
Date: 2026-10-16
Source: work-items/SEC-ROLE-001/closure.md (engineer reported a `JAVA_HOME` issue)
Applies to: any agent running `mvnw.cmd`
Status: ACTIVE
Lesson: Both APIs need JDK 21. When Maven fails before compiling, check that
`JAVA_HOME` points to a JDK 21 install first. The actual path is machine-specific
and belongs in the user-level agent file.

## L-2026-008 — Name long-running test exclusions up front
Date: 2026-10-18
Source: work-items/PARETO-001
Applies to: GVRP_Engine_API full test runs
Status: ACTIVE
Lesson: The full Engine suite takes more than an hour because benchmark tests
use real matrices. A task spec must name any test to be skipped in local
verification and get user approval for each, instead of letting the engineer
decide mid-run.

## L-2026-009 — Normalize line endings before byte comparison
Date: 2026-10-18
Source: work-items/PARETO-001
Applies to: reviews that prove existing tests were not modified
Status: ACTIVE
Lesson: With `core.autocrlf`, files can differ only in CRLF/LF. Normalize line
endings before comparing, and report a line-ending-only diff as such, not as a
test modification.

## L-2026-010 — Keep `package-lock.json` tracked and in sync
Date: 2026-10-19
Source: work-items/SEC-TENANT-001/closure.md, PARETO-002, CI-CLIENT-001
Applies to: GVRP_Entry_client_v2 dependency changes
Status: ACTIVE
Lesson: CI runs `npm ci`, which fails when the lock file is missing or out of
sync with `package.json` (a missing transloco entry broke it once). Commit the
regenerated lock file in the same change as the dependency edit.

## L-2026-011 — Stop the dev server before `npm ci`
Date: 2026-10-20
Source: work-items/CI-CLIENT-001
Applies to: GVRP_Entry_client_v2 on Windows
Status: ACTIVE
Lesson: `npm ci` failed while the dev server (`ng serve`, port 4200) was
running; stop it first (on Windows, open files in `node_modules` are the likely
cause, not verified). Five pre-existing Karma failures are tracked as
TEST-CLIENT-001; do not report them as new.
Update 2026-10-23: the full client suite ran 245/245 SUCCESS (fixed by `5cd1ae8`;
source: `work-items/DRIVER-MAP-004/verification-rereview.md`). The five-failure
note no longer holds; the `npm ci` part still applies. TEST-CLIENT-001 closed
2026-10-23 by user decision; `client-test` in `.github/workflows/ci.yml` is now
blocking (no `continue-on-error`).

## L-2026-012 — Verification runs mutate the local database
Date: 2026-10-19
Source: work-items/PARETO-002
Applies to: manual and runtime verification against `gvrp_db`
Status: ACTIVE
Lesson: Running optimization jobs for verification writes solutions and
revisions into the local database. Record which rows a verification created,
and do not compare database totals across runs without accounting for them.

## L-2026-013 — `recover` shows no scope changes without a checkpoint
Date: 2026-10-23
Source: Claude session 2026-10-23 (closing DRIVER-DELIVERY-001, DRIVER-MAP-001, DRIVER-MAP-004); `recover()` in `tools/feature-workflow/workflow.mjs`
Applies to: business-development workflow runs waiting in `await_user_test` or `accepted`
Status: ACTIVE
Lesson: `recover` compares the scope only with the last checkpoint, so a run
without one always reports `changes: []`, even when later commits changed the
scope. Before promising `accepted` or `finish`, compare `snapshot(root, scope)`
with `run.handoff`. If they differ, route `changed` → implement → verify to get
a new handoff, then take acceptance for that handoff.
