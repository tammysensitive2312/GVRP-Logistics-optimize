# Traceability — SEC-LOW-001

Inspected source revision: `0b6b759` (2026-11-10). Request: `request.md`. Design: `design.md`.

| Req | Behavior | Design | Code (planned) | Test | Status |
|---|---|---|---|---|---|
| LOW-R1 | Duplicate plate on create → 409, branch-neutral message, never 500 | D1 | `VehicleService.createVehicle`, `exception/ResourceConflictException`, `GlobalExceptionHandler` | LOW-T2, T5 | NOT_VERIFIED |
| LOW-R2 | Branch-scoped references checked before the plate | D1 | `VehicleService.createVehicle` | LOW-T1 | NOT_VERIFIED |
| LOW-R3 | Update to a taken plate → 409; own plate kept → OK; unique-key race → 409 | D1 | `VehicleService.updateVehicle`, `VehicleRepository.existsByVehicleLicensePlateAndIdNot` | LOW-T3, T4 | NOT_VERIFIED |
| LOW-R4 | Row ceiling → 400 before geocoding/DB | D2 | `OrderImportService`, `application.properties` | LOW-T6 | NOT_VERIFIED |
| LOW-R5 | Geocoding ceiling → 400 before any Goong call | D2 | `OrderImportService`, `application.properties` | LOW-T6 | NOT_VERIFIED |
| LOW-R6 | Whole revision tree deleted, deepest first | D3 | `JobCleanupService.deleteJobChain` | LOW-T7 | NOT_VERIFIED |
| LOW-R7 | Failing job isolated, rolled back, sweep continues | D3 | `JobCleanupService.cleanStatus`, per-job `TransactionTemplate` | LOW-T8 | NOT_VERIFIED |
| LOW-R8 | Existing behaviour unchanged | D1–D3 | — | full CI-style suite (347 before) | NOT_VERIFIED |

Decisions D1, D2, D3: Claude default, pending user acceptance (alternatives in `design.md`).
Schema: none applied; per-branch plate SQL in `design.md` D1 for the user only.
