# Task for the engineer — LEZ-001, P1b fix (send-guard lock)

You are `gvrp-engineer` (or a Claude Code session acting as the engineer). Implementation of P1 is authorized by the user (2026-10-26 22:54 +07:00). This task fixes finding **F1** of `review-p1b.md` and nothing else. The user asked for it on 2026-10-27 23:22 +07:00 ("viết task sửa lỗi khoá cho OpenCode"). Follow `AGENTS.md` in full (sections 14, 15 and 16). Run every command yourself from the repository root.

## Read first
1. `work-items/LEZ-001/review-p1b.md`: §3 F1, §4 (Deviation 7 rejected), §5
2. `work-items/LEZ-001/implementation.md` §P1b (N7, Deviation 7, Remaining Uncertainty)
3. `docs/engineering/LESSONS.md`: L-2026-007, -009, -014

If the fix below is wrong or impossible, stop and report. Do not redesign.

## The defect (F1)
`RouteSendService.sendRoute` is `@Transactional` and locks the route row with `routeRepository.findWithLockById` (PESSIMISTIC_WRITE, `SELECT … FOR UPDATE`). On a violation, `enforceLezCompliance` calls `RouteComplianceService.persistViolation`. That method is `@Transactional(propagation = REQUIRES_NEW)` and runs a bulk `update Route … where r.id = :id` on the same row from a second connection. The outer transaction holds the lock and waits for the inner call; the inner call waits for the lock. InnoDB waits `innodb_lock_wait_timeout`, then the request fails with a 500 instead of the 422, and the VIOLATION verdict is not stored.

## Step 0 — preconditions
1. Run `node tools/session-brief.mjs` and include the output.
2. Run `git status --short` on branch `feature/lez-001`. Expected: the P1a and P1b changes, `work-items/LEZ-001/*`, `docs/engineering/LESSONS.md`, `docs/security/SECURITY_CHECKLIST.md` (SEC-010 added by the reviewer). If anything else is modified or untracked (apart from `GVRP_*/.claude/`, `diff1*.txt`, `.mcp.json`), stop and report.
3. Run `node tools/feature-workflow/workflow.mjs recover work-items/LEZ-001/run.json`. State must be `implement`. Warnings on `work-items/LEZ-001/*` files are expected; continue.
4. **Exception hierarchy check.** Spring's `noRollbackFor` also applies to subclasses. List every class that extends `UnprocessablePlanException` (directly or indirectly), and every exception thrown in `sendRoute` and in the methods it calls **after** `enforceLezCompliance` returns (including `RevisionConflictException`). If any exception thrown after the guard is an `UnprocessablePlanException` or a subclass, **stop and report**: the fix below would then commit a half-done send.

## Step 1 — baseline
`GVRP_Entry_API`, `JAVA_HOME` = JDK 21 (L-2026-007):
- If the user has applied `2026-10-28-lez-compliance.sql`: `mvnw.cmd -B clean test`. Expected 243/0/0/0.
- Otherwise: `mvnw.cmd -B clean test "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"`. Expected 240/0/0/0.

Record which command you ran, the counts and every failing test by name. The Engine module is not touched; do not run it.

## What to change

Allowed files only:
- `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/service/RouteSendService.java`
- `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/service/lez/RouteComplianceService.java`
- `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/service/RouteSendServiceLezTest.java` (a P1b test, not yet accepted; see C3)
- one new test class, `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/service/RouteSendTransactionTest.java`
- `work-items/LEZ-001/implementation.md`, `work-items/LEZ-001/checkpoint-p1b-fix.json`

**C1. Commit the verdict with the refusal.**
- Annotate `sendRoute` with `@Transactional(noRollbackFor = UnprocessablePlanException.class)`.
- In `enforceLezCompliance`, remove the `persistViolation` call. `certify(route, violations, restrictions)` already writes the verdict on the managed, locked `route`; the commit on the way out of `sendRoute` stores it together with the legs filled by `fillMissingLegs`.
- Update the comment above the removed call and the method javadoc: the 422 now commits the verdict and the filled legs; nothing else has changed at that point because the supersede/insert block runs after the guard.
- The `LEZ_MIXED_DATES` 422 commits nothing (no entity changed). Keep it as is.

**C2. Remove `persistViolation`.**
- Search the whole `GVRP_Entry_API/src` for other callers. If there are none, delete the method and the imports it alone used (`Propagation`, `EntityManager`, `@PersistenceContext`, if unused).
- If another caller exists, stop and report.

**C3. Adjust the P1b send-guard tests.**
`RouteSendServiceLezTest` (SG-T1…SG-T4) is a new P1b file, not an accepted historical test. Replace only the assertions that expect `persistViolation`:
- the route returned by the mocked repository carries `lezStatus = VIOLATION`, `lezCheckedAt` set, `lezRulesetHash` equal to the compiled hash, `lezViolations` non-empty;
- the exception is `UnprocessablePlanException` and names the code, `seq` and zone key, as before;
- no `RouteSend` is saved and no sibling is superseded, as before.

No assertion may be weakened or removed for another reason. List every changed assertion under **Modified Tests** in `implementation.md`, with the reason "reviewer rejected Deviation 7 (review-p1b F1)". Do not touch any other test file.

**C4. New test `RouteSendTransactionTest`** (plain JUnit + reflection, DB-free):
- `sendRoute` has `@Transactional` whose `noRollbackFor` contains `UnprocessablePlanException`;
- no public method of `RouteComplianceService` carries `@Transactional(propagation = REQUIRES_NEW)`;
- `RevisionConflictException` and `DataInvalidException` are **not** assignable to `UnprocessablePlanException` (guards C1 against a later hierarchy change).

Do not write a `@SpringBootTest` or any test that writes to the local MySQL. The real lock behaviour is verified manually by the user (review §5 step 4).

## Step 2 — verification
1. Targeted: `mvnw.cmd -B test "-Dtest=RouteSendServiceLezTest,RouteSendTransactionTest,RouteSendServiceTest"`. All pass.
2. Full: the same command as Step 1. Expected: the baseline count + the new tests, 0 failures, 0 errors.
3. Static: `git diff --name-status` shows only the allowed files. `grep -rn "REQUIRES_NEW" GVRP_Entry_API/src/main` returns nothing in `service/lez`.

## Step 3 — record and checkpoint
- Add `## P1b fix` to `implementation.md` with the sections used for P1b (Scope, Baseline, Changes Made, Commands Executed, Verification Results, Modified Tests, Security-Relevant Changes, Remaining Uncertainty, Handoff).
- In §P1b Deviation 7, append one line: "Superseded by §P1b fix (review-p1b F1)." Do not rewrite the rest of §P1b.
- Remaining Uncertainty must say plainly that the real lock behaviour and the committed verdict are **NOT_VERIFIED** until the user runs review §5 step 4.
- Run `node tools/feature-workflow/workflow.mjs checkpoint work-items/LEZ-001/run.json work-items/LEZ-001/checkpoint-p1b-fix.json`. No state transition.

## Forbidden
- Any change outside the allowed files, including the other review findings F2–F8 (handled separately).
- Running a migration, or any INSERT/UPDATE/DELETE/DDL against `gvrp_db`.
- Changing, skipping or excluding a historical test, or the CI workflow.
- `git commit`, `git push`.
- Printing values from `application*.properties`.

## Handoff
Report in chat: Step 0 results (including the hierarchy list), baseline and final counts, the changed assertions, and the manual check the user must run:
> Enable a zone that bans the route's vehicle all day and covers one stop, then send the route: expect **422 within a few seconds**, then `GET /api/v1/solutions/{id}/compliance` shows that route `VIOLATION`. A second send of the same route must not call the engine again (legs already stored).
