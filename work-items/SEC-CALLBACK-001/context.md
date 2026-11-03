# Context — SEC-CALLBACK-001

Inspected 2026-11-04 by Claude Code on `audit/sec-audit-001` (source = `master` `c0cdfd5`). Paths relative to `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`.

## Current behavior

- `config/SecurityConfig.java`: `.requestMatchers("/api/v1/solutions/callbacks/**").permitAll()`; `/api/v1/driver/**` → DRIVER; `/api/v1/**` → PLANNER; `addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`.
- `security/ApiKeyAuthenticationFilter.java` (`@Component`, `OncePerRequestFilter`): `request.getRequestURI().startsWith("/api/v1/solutions/callbacks/")`; outside that prefix it passes the request on untouched. Inside: header `X-API-Key`, `engineApiKey.equals(apiKey)` (property `engine.api.key`), then sets `ENGINE_SERVICE` / `ROLE_ENGINE_SERVICE`. `getRequestURI()` is raw; Spring Security and MVC match the decoded path, so `/api/v1/solutions/%63allbacks/...` is "not a callback" for the filter but matches `permitAll` and the controller (runtime-confirmed).
- As a `@Component` filter it is also auto-registered as a plain servlet filter (in addition to the security chain).
- `controller/OptimizationCallbackController.java`: `@RequestMapping("/api/v1/solutions/callbacks")`, `POST /complete`, `/failed`, `/cancelled`, `/progress`.
- `service/OptimizationCallbackService.java#handleCompletion` → `saveSolution(job, callback)`: collects order ids from route stops and `unassigned_orders`, `orderRepository.findAllById(orderIds)` (unscoped, ~line 346); unassigned → `bulkTransition(..., UNASSIGNED)`; vehicles `vehicleRepository.findAllById(vehicleIds)` (unscoped, ~line 370); assigned → `bulkTransition(..., ON_ROUTE)`. No comparison with `job.getBranch()`. `Vehicle` has no `branch` field; its branch is `vehicle.getFleet().getBranch()`.
- `handleCompletion` catch-all marks the job FAILED on exception (`jobStatusUpdater.markAsFailed`), except `JobNotReadyException`.

## Tests that exist (by name)

- `security/RoleAccessTest.java`, `LezRoleAccessTest.java`, `LezComplianceRoleAccessTest.java`, `LezMembershipRoleAccessTest.java` — role rules incl. callback paths (read them; any assertion about callback `permitAll` behavior must keep passing).
- `service/OptimizationCallbackServiceLezTest.java`, `OptimizationCallbackServiceParetoTest.java`, `OptimizationCallbackUnassignedOnceTest.java` — stub `orderRepository.findAllById(any())` and `vehicleRepository.findAllById(any())`. **Keep those calls**; add the branch check on the loaded entities so these stubs stay meaningful. If their fixtures lack a branch on orders/fleets/vehicles and the new check fails them, stop and report (AGENTS.md §14) — do not edit them.

## Lessons

L-2026-001 (CI has no MySQL: tests DB-free), L-2026-003 (clean build), L-2026-004 (test libs present: spring-boot-starter-test, spring-security-test — verify in `pom.xml`), L-2026-007 (JAVA_HOME = JDK 21), L-2026-012 (manual check mutates the local DB — record ids).
