# Context — MAP-ORDERS-001

Inspected on 2026-10-29 by Claude, branch `feature/lez-001` (uncommitted LEZ-001 work on top of master). Static reading through the file bridge; the client was not running, so nothing was reproduced live (NOT_VERIFIED in a browser).

## 1. Current behaviour (with sources, all under `GVRP_Entry_client_v2/src/app/`)

| Fact | Source |
|---|---|
| `<app-map [orders]="orders">` receives `MainComponent.orders`. | `features/main/main.component.html` |
| `MainComponent.orders` is loaded **once** in `loadInitialData()` (`forkJoin` with vehicles and depots): `getOrders(currentFilter, 0, 20)` where `currentFilter.date = new Date().toISOString().split('T')[0]`. Nothing reloads it afterwards. The same list feeds `calculateStats()` (sidebar). | `features/main/main.component.ts` |
| `MainComponent.onOrderUpdated(updatedOrders)` exists (sets `orders`, recalculates stats) but is **not wired** to anything. | `main.component.ts`, `main.component.html` |
| The order table is `OrdersSectionComponent`: own `currentFilters` (default date via `toISOString`), own `currentPage`/`pageSize` (5), `loadOrders()` after filter, page, bulk update and order edit. Its only output to the parent is `orderClick`. | `features/main/orders/orders-section-view/orders-section.component.{ts,html}` |
| `OrdersFiltersComponent` sets both the default date and `maxDate` with `new Date().toISOString().split('T')[0]` (UTC). Between 00:00 and 07:00 +07:00 that is the previous day. | `features/main/orders/components/orders-filters/orders-filters.component.ts` |
| Consequence: the map shows only the first 20 orders of the UTC "today" at page load; the table's date/page changes never reach it. If that day has no orders, the map stays empty while the table lists orders. | the three files above |
| `MapComponent.ngOnChanges`: `solution` becoming null → `mapService.clearAll()`, which removes depot markers, order markers and routes; nothing redraws depots or orders afterwards. | `shared/components/map/map.component.ts`, `shared/services/map.service.ts` |
| `MapService.displaySolution` clears order markers (orders are hidden while a solution is shown; intended). | `shared/services/map.service.ts` |
| `MapService.loadOrders` calls `markerService.createOrderMarker` for every order; `L.marker([lat, lng])` throws on null/NaN coordinates, which aborts the `forEach`, so the remaining markers and the `fitBounds` are skipped. `OrderDTO.latitude/longitude` come from `Order.location`, which can be null. | `shared/services/map.service.ts`, `shared/services/marker.service.ts`, Entry `mapper/OrderMapper` |
| None of these files was changed by LEZ-001 P1c, except `map.service.ts` (P1c added zone overlay, stored geometry and `focusLeg`; `loadOrders`/`clearAll` untouched). The pending LEZ-001 task `task-opencode-p1c-review-fix.md` also edits `map.service.ts` (F-R3). | mtimes; `work-items/LEZ-001/*` |

## 2. Tests today

- No spec for `main.component`, `map.component` or `map.service`.
- `orders-section.component.spec.ts`, `orders-section-pareto.component.spec.ts` (with the LEZ-001 D8 provider), `orders-section-lez.component.spec.ts` (LEZ-001 P1c), `orders-filters.component.spec.ts` (does not assert today's date).
- Client CI command: `npx ng test --watch=false --browsers=ChromeHeadless` (blocking); last recorded 323 SUCCESS on `feature/lez-001` (LEZ-001 P1c follow-up).

## 3. Lessons that apply

L-2026-003 (clean verification), L-2026-009 (line endings in byte comparisons), L-2026-010 (no lock-file change here), L-2026-011 (stop `ng serve`), L-2026-014.

## 4. Out-of-scope observations

- Sidebar stats are computed from the 20 orders of the initial load only (not the whole day); not changed here.
- `maxDate` forbids future delivery dates in the table filter, which makes planning for tomorrow impossible from the table; recorded for a later decision, not changed.
