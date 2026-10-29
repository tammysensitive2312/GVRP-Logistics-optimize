# Feature traceability

Feature ID: BROWSER-FIX-001
Inspected source revision: `feature/lez-001` working tree (uncommitted), 2026-10-29 07:20–07:28 +07:00; implementation stacked on `fix/map-orders-001`
Request source: `work-items/BROWSER-FIX-001/request.md` (user messages 2026-10-29 07:20, 07:23, AskUserQuestion answers 07:25)

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| BF-R1 | Add Order works without `status` (07:20, 07:23) | E1 | Planned: `OrderInputDTO.status` | BF-T1; browser Add Order | Stack trace 07:18 (cause) | NOT_VERIFIED |
| BF-R2 | Validation failure → 400 per field, no rejected value logged | E2 | Planned: `GlobalExceptionHandler` | BF-T2 | None | NOT_VERIFIED |
| BF-R3 | Order dialog: server text, local date | C1 | Planned: `EditOrderDialogComponent` | BF-C1; browser | None | NOT_VERIFIED |
| BF-R4 | Select all matching orders (07:25) | C2 | Planned: `OrdersSectionComponent.onSelectAllClick`, `orders-section.component.html` | BF-C2; browser | None | NOT_VERIFIED |
| BF-R5 | Demand ≤ 2 decimals | C3 | Planned: `route-planning-dialog.component.html` | BF-C3 | None | NOT_VERIFIED |
| BF-R6 | Vehicle capacity shown | E3, C4 | Planned: `VehicleDTO.capacity`, `VehicleMapper.toDTO`, `vehicles-admin.component.html` | BF-T3; browser | None | NOT_VERIFIED |
| BF-R7 | Non-SCHEDULED refusal explains and names orders | E4 | Planned: `OptimizationJobService.validateAndLoadOrders` | BF-T4; browser | None | NOT_VERIFIED |
| BF-R8 | Job monitor mentions the LEZ panel | C5 | Planned: `job-monitoring-dialog.component.html` | BF-C4 | None | NOT_VERIFIED |
| BF-R9 | Permit actions visible | C6 | Planned: `vehicle-permits-dialog.component.{html,scss}` | BF-C5; browser | None | NOT_VERIFIED |
| BF-R10 | LEZ texts use zone names | E6 | Planned: `ComplianceChecker.label`, detail texts; `RouteComplianceService.noCompliantVehicleReason` | BF-T6, BF-T7; browser Recheck | None | NOT_VERIFIED |
| BF-R11 | Distinct names: unique copy name, rename COPIED (07:25) | E7, C7 | Planned: `ZoneService.createZone/updateZone`, `BranchZoneUpdateRequest`, `LezApiService.renameZone`, `ZoneEditorDialogComponent` | BF-T8, BF-T9, BF-C6, BF-C7; browser | None | NOT_VERIFIED |
| BF-R12 | One row per unassigned order | E5 | Planned: `OptimizationCallbackService.saveSolution` | BF-T5 | None | NOT_VERIFIED |

## Decision log

| Decision | Source | Affected |
|---|---|---|
| Fix every listed defect in one work item; MAP-ORDERS-001 stays separate and runs first | User 07:23; Claude | all |
| Select All = every order matching the filters, all pages | User 07:25 | BF-R4 |
| COPIED zone gets its own name and can be renamed | User 07:25 | BF-R11 |
| Copy keeps the template name when free, else `(copy)`, `(copy 2)`… (keeps `ZoneServiceTest.zsT2` valid) | Claude | BF-R11 |
| Zone texts quote the zone name (`zone "Test zone"`); stored texts not migrated | Claude | BF-R10 |
| Remove the duplicate `addAll` found while investigating | Claude | BF-R12 |
