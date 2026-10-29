# Feature traceability

Feature ID: MAP-ORDERS-001
Inspected source revision: `feature/lez-001` working tree (uncommitted), 2026-10-29
Request source: `work-items/MAP-ORDERS-001/request.md` (user messages 2026-10-29 00:12, 00:15 +07:00)

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| MO-R1 | Map shows the table's current orders and follows every reload (00:15) | D1 | Planned: `OrdersSectionComponent.ordersLoaded`, `MainComponent.mapOrders` / `onTableOrdersLoaded`, `main.component.html` | MO-T1, MO-T6; browser: change date and page | None | NOT_VERIFIED |
| MO-R2 | "Today" is the local date | D2 | Planned: `shared/utils/date.utils.ts#localDateIso`, three call sites | MO-T2, MO-T3; browser between 00:00 and 07:00 | None | NOT_VERIFIED |
| MO-R3 | Depots and orders return after clearing a solution | D3 | Planned: `MapService.clearSolution`, `MapComponent.ngOnChanges` | MO-T5; browser | None | NOT_VERIFIED |
| MO-R4 | Orders without coordinates are skipped, others still drawn | D4 | Planned: `coordinate.utils#hasMapCoordinates`, `MapService.loadOrders` | MO-T4; browser | None | NOT_VERIFIED |

## Decision log

| Decision | Source | Affected |
|---|---|---|
| Fix as a separate work item, not inside LEZ-001 | Claude proposal 2026-10-29 00:13, user "bạn viết task luôn đi" 00:15 | all |
| Sidebar stats and `maxDate` limit unchanged | Claude (request exclusions) | MO-R1, MO-R2 |
