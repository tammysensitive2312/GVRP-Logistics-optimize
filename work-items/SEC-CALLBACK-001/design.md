# Design — SEC-CALLBACK-001

## D1 — Authorize callbacks in Spring Security, not by raw URI (CB-R1, CB-R2)

- `SecurityConfig`: replace `.requestMatchers("/api/v1/solutions/callbacks/**").permitAll()` with `.requestMatchers("/api/v1/solutions/callbacks/**").hasRole("ENGINE_SERVICE")`, placed before the `/api/v1/**` PLANNER rule. Spring's matcher works on the same decoded/normalized path as MVC, so every encoding variant needs the role.
- `ApiKeyAuthenticationFilter`: drop the `getRequestURI().startsWith(...)` gate. When the `X-API-Key` header is present and valid, set the `ENGINE_SERVICE` authentication; when it is present and invalid, respond 401 as today; when absent, do nothing (the authorization rule then refuses callback paths with 401 via `jwtAuthenticationEntryPoint`). The filter never grants anything on non-callback paths beyond `ROLE_ENGINE_SERVICE`, which no other rule accepts.
- Prevent double registration as a plain servlet filter: add a `FilterRegistrationBean<ApiKeyAuthenticationFilter>` with `setEnabled(false)` (same pattern if `JwtAuthenticationFilter` already has one — check first and follow it).

## D2 — Constant-time key comparison (CB-R3)

`MessageDigest.isEqual(engineApiKey.getBytes(UTF_8), apiKey.getBytes(UTF_8))`. Never log the key.

## D3 — Branch-scoped completion (CB-R4, CB-R5)

In `OptimizationCallbackService.saveSolution` (or a small private helper called before anything is written):
- Keep `orderRepository.findAllById(orderIds)` and `vehicleRepository.findAllById(vehicleIds)`.
- `Long branchId = job.getBranch().getId()`. Refuse when: an order id is missing from the result, an order's `getBranch().getId()` ≠ `branchId`, a vehicle id is missing, or `vehicle.getFleet().getBranch().getId()` ≠ `branchId`.
- Refusal: throw a dedicated `CallbackRejectedException` (new, unchecked) **before** any `bulkTransition`, solution save or job update. `handleCompletion` must let it surface as HTTP 422 (or 400) through `GlobalExceptionHandler` and must not mark the job FAILED or COMPLETED for it (catch it before the catch-all, like `JobNotReadyException`). Log job id and counts only (no customer data).
- Order of checks matters: vehicles must be resolved before routes are built; do the check right after both lookups.

## D4 — Tests (new files only)

| ID | Case | Expected |
|---|---|---|
| CB-T1 | MockMvc (full security chain, `@SpringBootTest` + `@AutoConfigureMockMvc` or `@WebMvcTest` with `SecurityConfig` imported, DB-free with mocked service): `POST /api/v1/solutions/callbacks/progress` no key | 401, service not called |
| CB-T2 | Same with `/api/v1/solutions/%63allbacks/progress`, `/api/v1/%73olutions/callbacks/progress`, `/api/v1/solutions/callbacks/progress/` , `/api/v1/solutions//callbacks/progress` (whichever the firewall does not reject) | each 401 or 400 (firewall); never 2xx; service never called |
| CB-T3 | Valid key, plain and encoded path | 200, service called once |
| CB-T4 | Wrong key | 401 |
| CB-T5 | PLANNER JWT on a callback path | 403/401, service not called |
| CB-T6 | Service: completion for job of branch A with an unassigned order of branch B | `CallbackRejectedException`; no `bulkTransition`, no `solutionRepository.save`, job status unchanged |
| CB-T7 | Service: route with a vehicle whose fleet is branch B | rejected, nothing saved |
| CB-T8 | Service: unknown order id | rejected |
| CB-T9 | Service: all same-branch | saved as today (existing tests also cover) |

If a DB-free MockMvc test of the chain is impossible, stop and report.

## Risks

- Engine must keep sending `X-API-Key` on every callback (it does today). Any other caller of `/api/v1/solutions/callbacks/**` without the key stops working — intended.
- Existing role-access tests may assert the old `permitAll` behavior for callbacks; if one fails, stop and report (do not change it).
