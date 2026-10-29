# Context — BROWSER-FIX-001

Inspected 2026-10-29 07:20–07:28 (+07:00) by Claude on the `feature/lez-001` working tree (read-only). Paths are relative to `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/` (Entry) and `GVRP_Entry_client_v2/src/app/` (client).

## Causes found

| Req | Evidence | Cause |
|---|---|---|
| BF-R1 | Stack trace 07:18: `MethodArgumentNotValidException … field 'status': rejected value [SCHEDULED] … Order status must be null when creating`. Client payload (captured in the browser) has **no** `status` key: `edit-order-dialog.component.ts` strips it (`const { status, ...createPayload } = this.form`). | `dto/request/OrderInputDTO.java`: `@Null(groups = OnCreate.class) private OrderStatus status = OrderStatus.SCHEDULED;` — Jackson uses the no-args constructor, so the field initializer fills `SCHEDULED` and the `@Null` check fails on every create. `OrderMapper.toEntity` already ignores `status` and `Order` defaults to `SCHEDULED` (`@Builder.Default`); `updateEntityFromDTO` uses `NullValuePropertyMappingStrategy.IGNORE`. `@Builder` without `@Builder.Default` already leaves `status` null for builder callers (import). |
| BF-R2 | Same trace: `GlobalExceptionHandler - Unexpected system error` → `Completed 500`. | `exception/GlobalExceptionHandler.java` has handlers for `HttpMessageNotReadableException` and `HttpRequestMethodNotSupportedException` but none for `MethodArgumentNotValidException`; it falls to `handleGenericException`, which also logs the full exception including rejected values (customer data). |
| BF-R3 | `edit-order-dialog.component.ts`: `delivery_date: new Date().toISOString().split('T')[0]` (UTC date: 28/10 at 07:00 +07:00); errors shown with `err.message` (interceptor text "Internal server error."). | UTC date; server text not resolved (`serverErrorMessage` exists in `shared/utils/api-error.utils.ts`). MAP-ORDERS-001 adds `shared/utils/date.utils.ts#localDateIso`. |
| BF-R4 | `orders-action-toolbar.component.ts` emits `selectAllClick`; `orders-section.component.html` binds `importClick, addClick, planRoutesClick, deleteSelectedClick, exportClick, bulkEditClick` — **not** `selectAllClick`; no handler in `orders-section.component.ts`. | Unwired output. Selection is single-page by design (`onFilterChange` / `onPageChange` clear `selectedOrders`); `OrdersTableComponent` keeps its own `SelectionModel`, clears it and emits `[]` on every `orders` change, emits on every row toggle. `GET /api/v1/orders?page=0&size=30000&date=2026-08-21` returned all 25 054 rows in the browser check (no server page cap observed). |
| BF-R5 | `features/main/jobs/route-planning-dialog/route-planning-dialog.component.html` line 33: `{{ data.totalDemand }} kg`; `orders-section.onPlanRoutesClick` sums `demand` as floats. | No number formatting. |
| BF-R6 | `vehicles-admin.component.html` line 55: `{{ vehicle.capacity }} kg`; client `VehicleDTO.capacity: number`; Entry `dto/response/VehicleDTO.java` has no `capacity`; `mapper/VehicleMapper.toDTO` maps `vehicleType.id/typeName` only; `entity/VehicleType.capacity` is `Integer`, not null. | Server DTO lacks the field. |
| BF-R7 | `service/OptimizationJobService.validateAndLoadOrders`: orders with `status != SCHEDULED` → `DataInvalidException(code RESOURCE_CONFLICT "0040901", message "Resource conflict.", resource "orders", field = all codes joined)`; the client shows only the message. `OptimizationCallbackService.saveSolution` moves assigned orders to `ON_ROUTE` and unassigned ones to `UNASSIGNED`. | Generic message. Re-planning orders of a finished job is refused by design. |
| BF-R8 | `job-monitoring-dialog.component.html`: unserved alert "… Check capacity, time windows and the number of selected vehicles." | Static text; no mention of LEZ. |
| BF-R9 | `vehicle-permits-dialog.component.html`: `<td class="row-actions">` and `.scss` `.row-actions { display: flex; … }`. Dialog opened at `width: 760px` (`vehicles-admin.component.ts`). | `display: flex` on a `td` takes it out of table layout; the table overflows and the delete icon is clipped. |
| BF-R10 | `service/lez/ComplianceChecker.check`: six detail strings use `zone.getKey()` ("Stop is in zone Z2 …", "Leg crosses zone Z2 …", "Missing vehicle data decides transit through Z2"). `service/lez/RouteComplianceService.noCompliantVehicleReason`: `"No compliant vehicle for zone " + String.join(", ", orderZoneKeys)`. `CompiledRestrictions.CompiledZone` has `name`. | Keys used in user-facing text. |
| BF-R11 | `service/lez/ZoneService.createZone` COPIED: `.name(template.getName())`; `updateZone` accepts only `enabled` (`dto/request/BranchZoneUpdateRequest`: `@Data @NoArgsConstructor @AllArgsConstructor`, one field). `BranchZone.name` is `length = 200`, COPIED only; LINKED name = template name (`toDTO`). No rename endpoint; client `LezApiService.setZoneEnabled` sends `{enabled}`. | Copy duplicates the template name; no way to rename. |
| BF-R12 | `OptimizationCallbackService.saveSolution`: `solution.getUnassignedOrders().addAll(unassignedList);` appears **twice** in a row. | Copy/paste duplicate. |

## Existing tests that constrain the design (must stay unchanged and green)

- `ZoneServiceTest.zsT2_copyVersion2_copiesGeometryAndV2Rules` asserts the copy name `"Test zone"`; the branch-zone list is unstubbed (Mockito returns an empty list). → the copy keeps the template name when no other zone of the branch uses it.
- `ZoneServiceTest.zsT4_…` calls `new BranchZoneUpdateRequest(false)` and expects 7 `findByIdAndBranchId` calls. → keep a one-argument `(Boolean enabled)` constructor; `updateZone` keeps its lookup first.
- `OptimizationCallbackServiceLezTest.cbT1_unassignedOrderWithNoCompliantVehicle_getsTheLezReason` asserts the reason **contains "Z1"**; fixture zone names are `"Zone " + key` (`LezFixtures.zone`). `cbT1_routeIsCertifiedByTheJobSnapshot…` asserts the violations JSON contains `"ZONE_ACCESS"` and `"Z1"` (the `zone_key` field stays).
- `ComplianceCheckerTest` asserts codes, seq, order id, `zoneKey` and `detail().contains(ATTR_GROSS_WEIGHT)` only.

## Not verified

- Whether other existing tests assert the exact `ComplianceChecker` detail texts or the "Resource conflict." message: the engineer checks in the baseline step and stops if one does.
- Live behaviour of every change (browser check after the review).
