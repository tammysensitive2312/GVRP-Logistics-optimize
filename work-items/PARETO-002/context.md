# Context — PARETO-002

`C` = `GVRP_Entry_client_v2/src/app`. Static read on 2026-10-18.

- Angular 18.2 standalone components, signals, `ChangeDetectionStrategy.OnPush`, Angular
  Material 18, Leaflet, Transloco (`@jsverse/transloco`). No chart library in
  `package.json` -> the chart is inline SVG.
- JSON is snake_case everywhere except `GET /jobs/{id}/progress` (raw camelCase map,
  `C/core/models/job.model.ts` `JobProgressDTO`).
- `C/features/main/orders/orders-section-view/orders-section.component.{ts,html}`:
  `mat-tab-group` with tabs Orders / Route / Timeline; Route and Timeline are disabled
  while `solution()` is null. `solution` comes from `SolutionStore`
  (`C/core/services/solution.store.ts`, in-memory signal, `setSolution`, `loadById`).
- `C/features/main/orders/route-view/route-view.component.ts` is the reference for
  revision handling: `api.saveRouteRevision` -> `store.setSolution(saved)`; on 409
  `loadLatestAndExit()` uses `api.latestRevisionId(id)` then `store.loadById(id)`;
  error messages read `error.errors[0].message`.
- `C/core/services/api.service.ts` holds all HTTP calls (`${environment.apiUrl}/...`).
- `C/features/main/jobs/job-monitoring-dialog`: progress from `JobPollingService`;
  `phaseLabel`, `solverStats`; result cards after completion.
- `C/features/main/jobs/route-planning-dialog`: `enablePareto` checkbox with hint
  "Generate multiple solutions for trade-off analysis"; `estimatedTimeMap` per speed.
- `C/features/main/orders/route-view/send-route-dialog`: revision picker fed by
  `api.revisionChain` (`RevisionDTO.type`).
- `C/shared/services/confirm.service.ts` (`ConfirmService.ask({title, message, confirmText,
  cancelText, danger})`) and `C/shared/services/toast.service.ts` exist.
- Shared formatting: `C/shared/utils/solution-view.utils.ts` (`formatVndAmount`,
  `formatDuration`, `routeColor`), `C/shared/pipes/vnd.pipe.ts`.
- Units from the backend: cost VND, distance km, CO2 kg, time hours.
- Specs: Karma + Jasmine; `C/../testing/transloco-testing.ts` helper.
