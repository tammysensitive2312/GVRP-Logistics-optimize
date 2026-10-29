# Design — MAP-ORDERS-001

Inputs: `request.md` (MO-R1…R4), `context.md`. Technical design by Claude; the user asked for the fix on 2026-10-29 00:15 +07:00. Client only (`GVRP_Entry_client_v2/src`), no new dependency.

## 1. Changes

**D1 — The map follows the table (MO-R1).**
- `OrdersSectionComponent` gains an output `ordersLoaded: OrderDTO[]`, emitted after every **successful** `loadOrders()` with the loaded page (a new array). A failed load emits nothing (the map keeps its last markers).
- `MainComponent` gains a field `mapOrders: OrderDTO[]` (initially `[]`) and a handler `onTableOrdersLoaded(orders)` that assigns a new array to it. `main.component.html` binds `<app-map [orders]="mapOrders">` and `(ordersLoaded)="onTableOrdersLoaded($event)"`.
- `MainComponent.orders`, its initial load and `calculateStats()` stay as they are (sidebar stats unchanged, request exclusions). `onOrderUpdated` stays unused and unchanged.

**D2 — Local "today" (MO-R2).**
- New pure helper `shared/utils/date.utils.ts`: `localDateIso(date: Date = new Date()): string` → `yyyy-MM-dd` from `getFullYear/getMonth/getDate`.
- Use it in `MainComponent.currentFilter`, `OrdersSectionComponent.currentFilters` and `OrdersFiltersComponent` (default `date` and `maxDate`). Other `toISOString().split('T')[0]` uses in the client are listed in the record, not changed.

**D3 — Redraw after clearing a solution (MO-R3).**
- `MapService.clearSolution()` (new): clears route and zone layers and the focus registry (and the LEZ focus highlight, if the LEZ-001 review fix added one), sets `solutionDisplayed = false`; it does **not** touch depot or order markers. `clearAll()` stays for other callers.
- `MapComponent.ngOnChanges`: when `solution` becomes null, call `clearSolution()`, then `loadDepots(this.depots)` and the existing `loadOrderMarkers()`.

**D4 — Skip orders without coordinates (MO-R4).**
- New pure helper `hasMapCoordinates(order)` (finite latitude in [-90, 90] and longitude in [-180, 180]) in `shared/utils/coordinate.utils.ts`.
- `MapService.loadOrders` draws only orders that pass it; when some are skipped, one `console.warn` with the **count only** (no id, code, address or coordinate; AGENTS.md §15).

## 2. Tests (new files; Jasmine/Karma; no real HTTP)

| ID | Case | Expected |
|---|---|---|
| MO-T1 | `OrdersSectionComponent`: `getOrders` returns a page → `ordersLoaded` emits that page; change page and date → emits again with the new page; `getOrders` errors → no emission | as stated |
| MO-T2 | `localDateIso(new Date(2026, 9, 29, 0, 12))` (local constructor) | `'2026-10-29'`; the result equals the local getters, never the UTC date |
| MO-T3 | `OrdersFiltersComponent` default `date` and `maxDate` equal `localDateIso()` | as stated |
| MO-T4 | `hasMapCoordinates` for null, NaN, out-of-range and valid values | false / false / false / true |
| MO-T5 | `MapComponent` with a `MapService` spy: solution set then set to null after the map is ready | `clearSolution`, `loadDepots` and `loadOrders` called; `clearAll` not called |
| MO-T6 | `MainComponent` wiring (new spec, dependencies stubbed, child components shallow): `onTableOrdersLoaded(list)` sets `mapOrders` to that list; `orders` and `stats` unchanged | as stated |

If a test of any row is impossible without a live Leaflet map, extract the decision into a pure function and test that; if even that is impossible, stop and report.

## 3. Risks

- `map.service.ts` is also edited by the pending LEZ-001 review fix (F-R3); this task runs **after** that fix and on top of it (stacked branch).
- The map now shows the table's page (default 5 orders) instead of 20 orders of the day: intended by MO-R1 ("the orders the table lists").
