# Verification — SEC-TENANT-001

Environment: user's Windows machine, Java 21.0.1, Maven wrapper, local MySQL
8.0.39 in Docker. Run by `run-verify.ps1` on 2026-10-16 20:26 (+07:00), branch
`fix/sec-tenant-001` created from `master` at `7a0c4ceb8b29271fb7ee748b62c4eb1c42dfa644`
with the changes uncommitted. Raw output: `mvn-test.log`, `verify-output.log`
(local, gitignored).

## Checks

| Check | Result |
|---|---|
| `mvnw.cmd -B test` in `GVRP_Entry_API` (full suite, including the two `@SpringBootTest` classes against MySQL) | BUILD SUCCESS. Tests run: 69, Failures: 0, Errors: 0, Skipped: 0 |
| New tests (21) | `SolutionServiceTest` 6/6, `VehicleServiceTest` 6/6, `FleetServiceTest` 2/2, `RouteCustomizationServiceTest` 13/13 (8 existing + 5 new), `RouteSendServiceTest` 12/12 (10 existing + 2 new) |
| Pre-existing tests (48) | all pass, including `SolutionCustomizationControllerTest` 7/7 after the call-site update |
| Workflow ledger | `init` and four `ready` transitions recorded; run at `verify`, revision 4 |

### Note on compilation

Maven reported "Nothing to compile - all classes are up to date" for main
sources, because the IDE had already compiled the edited files into
`target/classes`. Evidence that the tests ran against the new code: test
compilation (13 files) succeeded against the new method signatures, and the new
refusal tests pass (`routeSends_otherBranch_notFound`,
`getSolutionDetail_otherBranch_notFound`); both fail on the old code paths.
A clean build (`mvnw.cmd clean test`) was not run.

## Requirement coverage

| Req | Evidence | Status |
|---|---|---|
| TI-001 | `SolutionServiceTest` (detail, revision chain), `RouteCustomizationServiceTest.latestRevisionId_*` | VERIFIED_BY_TEST (unit, mocks) |
| TI-002 | `RouteCustomizationServiceTest.evaluate_otherBranch_*`, `saveRevision_otherBranch_*` (no engine call, no save) | VERIFIED_BY_TEST (unit, mocks) |
| TI-003 | `VehicleServiceTest.createVehicle_*` | VERIFIED_BY_TEST (unit, mocks) |
| TI-004 | `VehicleServiceTest.updateVehicle_*` | VERIFIED_BY_TEST (unit, mocks) |
| TI-005 | `FleetServiceTest` | VERIFIED_BY_TEST (unit, mocks) |
| TI-006 | `RouteSendServiceTest.routeSends_*` | VERIFIED_BY_TEST (unit, mocks) |
| TI-007 | null-branch tests; controllers read `CurrentUserUtil.getCurrentBranchId()` (static inspection) | PARTIALLY_VERIFIED |
| TI-008 | 21 new tests; existing assertions unchanged (see `implementation.md`) | VERIFIED_BY_TEST |

## Not executed / unverified

- End-to-end HTTP check with two real branches (user test in `handoff.md`).
- Spring Data derived queries `findByIdAndFleetBranchId`, `findByIdAndBranchId`
  against the database: the application context started (repository methods
  are validated at startup), but no test queried them.
- Independent review of the diff (gvrp-reviewer or `/security-review`).
- Clean rebuild.
