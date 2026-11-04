# Blocker — SEC-CALLBACK-001 design D3 (vehicle branch check)

Date: 2026-11-04. Found before any source change (run state `implement`, rev 3).

## Finding

Design D3 requires refusing a completion callback when
`vehicle.getFleet().getBranch().getId() != job.getBranch().getId()`.
Existing tests build route vehicles without a fleet:

- `service/OptimizationCallbackServiceLezTest.java:109` — `Vehicle.builder().id(VEHICLE).build()` (shared `setUp`, used by every test that stubs `vehicleRepository.findAllById`, line 171)
- `service/OptimizationCallbackServiceParetoTest.java:151` — `Vehicle.builder().id(7L).build()` (test at line 140)
- `service/OptimizationCallbackUnassignedOnceTest.java:75` — `Vehicle.builder().id(7L).build()`

All three send a route with that vehicle, so the specified check either throws
NPE or refuses the callback, and these tests fail. Per design D3 / context.md
and AGENTS.md §14 the engineer must stop and must not edit them.

The order half of D3 does not conflict: every fixture order has the job's
branch, and the Pareto tests without order stubs send no routes.

In production `Vehicle.fleet` and `Fleet.branch` are `nullable = false`, so a
null fleet occurs only in these fixtures.

## Options for the user

A. Approve a fixture-only change in the three files: give each vehicle
   `Fleet.builder().branch(branch).build()` (same branch as the job). Assertions
   unchanged. Then implement D3 exactly as designed.
B. Implement D1, D2 and the order half of D3 now; defer the vehicle check to a
   follow-up item (SEC-013 stays partially open: a forged callback could still
   attach a foreign vehicle to a route of the job's own solution, but could not
   change any foreign order).
C. Not recommended: treat a vehicle with a null fleet as acceptable (fail-open);
   it exists only to keep fixtures green.

## Baseline (`mvnw.cmd -B clean test`, JDK 21, this worktree, exit 1)

Tests run: 331, Failures: 0, Errors: 3 — all pre-existing, environment-related:
`DepotMapperTest.testToDTO`, `DepotMapperTest.testMapperBeanIsCreated`,
`GvrpEntryApiApplicationTests.contextLoads` (`@SpringBootTest`; context fails on
`jwtTokenProvider`: WeakKeyException, JWT secret resolves to 0 bytes in this
worktree — the gitignored local secret config is not present here). Both
classes are in the CI exclusion list. Log: `GVRP_Entry_API/baseline-test.log`.
