# Implementation — MAP-ORDERS-001

Engineer: Claude Code (acting as `gvrp-engineer` for `task-opencode.md`), 2026-10-29, Windows host shell.
Branch: `fix/map-orders-001`, created from `feature/lez-001` @ `8682ceb`. Nothing in this task is committed.

## Scope

Client only (`GVRP_Entry_client_v2/src/app`), design D1–D4 of `design.md`:
the planner map follows the order table (D1), "today" is the local date (D2), clearing a solution redraws depots and orders (D3), orders without usable coordinates are skipped (D4). No dependency, no `package*.json` / `angular.json` / `public/**` change, Entry/Engine untouched.

## Baseline

Taken on `fix/map-orders-001` before any code edit (same tree as `feature/lez-001` @ `8682ceb`).

| Check | Result |
|---|---|
| `npx ng test --watch=false --browsers=ChromeHeadless` | EXIT=0, **339 of 339 SUCCESS**, 0 failing specs |
| `npm run build` | EXIT=0, 0 ERROR, 12 warnings: initial bundle budget (665.96 kB vs 512 kB); component style budget for `job-monitoring-dialog`, `login`, `zones-admin`, `sidebar`, `fleet-setup`, `orders-section`, `import-orders-dialog`, `route-view`, `orders-table`, `edit-order-dialog` (10); `leaflet` not ESM (used by `driver-view.component.ts`) |

## Changes Made

| File | Change | Design |
|---|---|---|
| `shared/utils/date.utils.ts` (new) | `localDateIso(date = new Date())` → `yyyy-MM-dd` from local getters | D2 |
| `shared/utils/coordinate.utils.ts` | added `hasMapCoordinates(point)` (finite lat in [-90, 90], lng in [-180, 180]); `formatCoordinate` unchanged | D4 |
| `features/main/orders/orders-section-view/orders-section.component.ts` | new `@Output() ordersLoaded: OrderDTO[]`, emitted with a new array after every successful `loadOrders()` (not on error); default `currentFilters.date` = `localDateIso()` | D1, D2 |
| `features/main/main.component.ts` | new `mapOrders: OrderDTO[] = []` and `onTableOrdersLoaded(orders)`; `currentFilter.date` = `localDateIso()`. `orders`, initial load, `calculateStats()`, `onOrderUpdated` unchanged | D1, D2 |
| `features/main/main.component.html` | `<app-map [orders]="mapOrders">`; `(ordersLoaded)="onTableOrdersLoaded($event)"` on `<app-orders-section-view>` | D1 |
| `features/main/orders/components/orders-filters/orders-filters.component.ts` | default `date` and `maxDate` = `localDateIso()` (maxDate semantics unchanged) | D2 |
| `shared/services/map.service.ts` | new `clearSolution()` = existing `clearRoutes()` (routes, zone layer, LEZ highlight layer, focus registry, `solutionDisplayed = false`; depot/order markers untouched); `loadOrders` draws only `hasMapCoordinates` orders, one `console.warn` with the skipped **count only**; fitBounds when ≥1 drawable order. `clearAll()` kept | D3, D4 |
| `shared/components/map/map.component.ts` | solution → null: `clearSolution()`, `loadDepots(this.depots)`, `loadOrderMarkers()` (was `clearAll()`); an `orders` change is drawn only while no solution is shown (see Deviations) | D3 |

New spec files (24 new tests):

| File | Tests | Case |
|---|---|---|
| `features/main/orders/orders-section-view/orders-section-map.component.spec.ts` | 4 | MO-T1 (emit on load, page + date change, none on error), D2 initial request date |
| `shared/utils/date.utils.spec.ts` | 4 | MO-T2 |
| `features/main/orders/components/orders-filters/orders-filters-date.component.spec.ts` | 1 | MO-T3 |
| `shared/utils/coordinate.utils.spec.ts` | 5 | MO-T4, `formatCoordinate` unchanged |
| `shared/components/map/map.component.spec.ts` | 3 | MO-T5, orders hidden while a solution is shown, new page drawn without a solution |
| `shared/services/map.service.spec.ts` | 3 | D4 skip + count-only warning, no warning when all valid, D3 `clearSolution` keeps order markers (real Leaflet map in a sized div, `MarkerService` spy) |
| `features/main/main.component.spec.ts` | 4 | MO-T6 (handler, template binding of `[orders]`, initial empty), D2 dashboard date |

## Commands Executed

All from the repository root unless a module directory is named.

| Step | Command | Exit / result |
|---|---|---|
| 0.1 | `ls work-items/LEZ-001/checkpoint-p1c-review-fix.json` | 0, exists |
| 0.2 | `git log -1 --oneline`, `git status --short` | first run: `b057218`, 172 disallowed uncommitted paths → stopped and reported. On user request ("Bạn commit giúp") Claude committed `900c567` (LEZ-001 state, 222 files, secret-pattern scan of the staged diff: no hits, no `*.properties` / `.env` / `*.log`) and `8682ceb` (BROWSER-FIX-001 work-item docs). Re-check: only `work-items/MAP-ORDERS-001/` uncommitted |
| 0.3 | `node tools/session-brief.mjs` | 0 (output summarised below) |
| 0.4 | `git switch -c fix/map-orders-001` | 0 |
| 0.5 | stop `ng serve` (PID 15636, `ng.js serve --host 0.0.0.0 --proxy-config proxy.conf.json`) | **not done**: the stop was refused by the agent permission classifier (see Deviations) |
| 1 | `workflow.mjs init work-items/MAP-ORDERS-001/run.json . MAP-ORDERS-001 GVRP_Entry_client_v2/src` | 0, `intake` rev 0 |
| 1 | `workflow.mjs step … ready 01-proof.json` / `02-proof.json` / `03-proof.json` | 0 each: `discover` rev 1, `design` rev 2, `implement` rev 3 |
| 2 | `GVRP_Entry_client_v2: npx ng test --watch=false --browsers=ChromeHeadless` | 0, 339 SUCCESS |
| 2 | `GVRP_Entry_client_v2: npm run build` | 0, 12 warnings |
| 3 | `npx ng test …` (first run after implementation) | 1, 360 SUCCESS / 3 FAILED — all three in the new `map.service.spec.ts`: `Invalid LatLng (NaN, NaN)` because the test map had no `maxZoom` (no tile layer → fitBounds on one marker zooms to Infinity). Fixed in that new spec's setup (`maxZoom: 18`); no production change |
| 3 | `npx ng test …` | 0, **363 SUCCESS** |
| 3 | `rm -rf dist` then `npm run build` (clean, L-2026-003) | 0, 0 ERROR, 12 warnings, identical to baseline (sorted text diff) |
| 3 | `git status --short`; `grep -rn "toISOString().split('T')[0]" GVRP_Entry_client_v2/src/app` | see Verification |
| 4 | `workflow.mjs step … ready 04-proof.json` | 0, `verify` rev 4 |

Session brief (Step 0.3, 2026-10-29T00:34Z): branch `feature/lez-001` @ `8682ceb`; open work item LEZ-001 `implement` rev 10 (next action: Claude re-reviews; user runs the P1c browser check and P1b review §5 step 4; issues: "Scoped files changed; inspect and reverify affected work", "Some historical evidence is changed or unavailable"); security not FIXED: SEC-001 (Critical), SEC-010 (Medium), SEC-004/005/007/008 (Low); 14 of 14 lessons active; `PROJECT_STATE.md` last changed 2026-10-13 with 10 work items finished since.

## Verification Results

- Unit tests: 363 SUCCESS = baseline 339 + 24 new. VERIFIED.
- Build: EXIT=0, no ERROR, no new budget warning (same 12 as baseline). VERIFIED.
- `git status --short`: only the 7 modified + 8 new files listed above under `GVRP_Entry_client_v2/src/app/`, plus `work-items/MAP-ORDERS-001/`. VERIFIED.
- Other `toISOString().split('T')[0]` in `src/app`, **not changed** (out of scope):
  - `features/main/orders/components/edit-orders-dialog/edit-order-dialog.component.ts:59` — default `delivery_date` of a new order in the Add/Edit dialog (UTC "today"; BROWSER-FIX-001 BF-R3 covers it).
  - `features/main/orders/components/import-orders-dialog/import-orders-dialog.component.ts:37` — default `deliveryDate` of the import dialog (UTC "today").
- Live browser check: not run. NOT_VERIFIED.

## Modified Tests

None. No existing spec, fixture or helper was edited, disabled, skipped, focused or excluded.

## Security-Relevant Changes

- New log line: `console.warn('<n> order(s) without usable coordinates not shown on the map')` — count only, no id, code, address or coordinate (asserted in `map.service.spec.ts`).
- No endpoint, query, authentication, `permitAll`, secret or dependency change. Client data flow unchanged: the map now shows the page the table already fetched from the branch-scoped orders endpoint; no new request.

## Deviations From Design

1. **Order markers stay hidden while a solution is shown** (`map.component.ts`: `changes['orders'] && !this.solution`). Not written in D1/D3, added to keep a task constraint: before this change `orders` reached the map only once at start, so in practice orders stayed hidden under a solution; with D1 every table reload would have redrawn order markers over the routes, which the task forbids ("hiding orders while a solution is shown" must not change). When the solution is cleared, D3 draws the table's latest page. Tested in `map.component.spec.ts`. Reviewer decision: accept or move.
2. **`ng serve` not stopped** (Step 0.5, L-2026-011): the agent's permission classifier refused to stop the user's process. No `npm ci` or dependency change was run, which is what L-2026-011 protects; tests and the build ran with the dev server alive and succeeded. The dev server may hot-reload the changed files.
3. Extra new spec file `shared/services/map.service.spec.ts` (not named in the task's example list); covers the D4 filter and the D3 marker retention. Adding tests is allowed (AGENTS.md §14).
4. Step 0.2 precondition was met by Claude committing on the user's instruction, not by the user (commits `900c567`, `8682ceb` on `feature/lez-001`, not pushed).

## Remaining Uncertainty

- Browser behaviour (markers follow page/date changes, redraw after Clear solution, local date between 00:00 and 07:00 +07:00, page with a coordinate-less order): NOT_VERIFIED; only unit tests with spies and a headless Leaflet map.
- The map now shows the table's page (default 5 orders) instead of the first 20 orders of the day (design §3, intended).
- When the table reloads while a solution is shown, the map does not change until the solution is cleared (Deviation 1).
- Sidebar stats still come from the 20-order initial load (out of scope, context §4).

## Lesson Candidates

- Leaflet `fitBounds` on a single point in a test map without a tile layer throws `Invalid LatLng (NaN, NaN)`: set `maxZoom` on test maps. Small; candidate only.

## Handoff to Reviewer

- Review `map.component.ts` Deviation 1 first, then D4 in `map.service.ts` (`loadOrders`).
- Re-run: `cd GVRP_Entry_client_v2 && npx ng test --watch=false --browsers=ChromeHeadless` (expect 363 SUCCESS) and `npm run build` (EXIT=0, 12 warnings).
- Optional live check (planner account): date with orders → change page and date → markers follow; plan/load a solution, change page, Clear solution → depots and the current page's orders reappear.
- `traceability.md` still says "Planned"/NOT_VERIFIED; it is not in this task's allowed files and is left for the reviewer.
