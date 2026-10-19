# Handoff — PARETO-002

## Exact version

- Branch `feature/pareto-002`, HEAD `4e2f928`, **working tree with the PARETO-002 changes
  uncommitted** (nothing was committed for this work item).
- Scope of the changes (`GVRP_Entry_client_v2/src/app`):
  - new: `core/models/pareto.model.ts`, `shared/utils/pareto-chart.utils.ts`,
    `features/main/orders/pareto-view/pareto-view.component.{ts,html,scss}`, 6 new `*.spec.ts`;
  - modified: `core/models/index.ts`, `core/models/solution.model.ts`, `core/models/job.model.ts`,
    `core/services/api.service.ts` (+`getPareto`, `applyParetoCandidate`, `latestRevisionId` is
    pre-existing), `features/main/orders/orders-section-view/orders-section.component.{ts,html}`,
    `features/main/jobs/job-monitoring-dialog/job-monitoring-dialog.component.{ts,html}`,
    `features/main/jobs/route-planning-dialog/route-planning-dialog.component.html`,
    `features/main/orders/route-view/send-route-dialog/send-route-dialog.component.{ts,html}`.
- Backend dependency: PARETO-001 (already merged at `4e2f928`); its migration is applied on the dev
  database (the live run exercised it successfully).

## How to run it locally

1. Engine `:8081`, Entry API `:8080`, MySQL dev DB, then `npm start` in `GVRP_Entry_client_v2`.
2. Log in as a planner of the dev branch.

## User test steps and expected results

| # | Step | Expected |
|---|---|---|
| 1 | Orders screen → set the delivery-date filter to `2026-08-21` (the data date; the default "today" filter shows 0 rows) | the order list is populated |
| 2 | Select only **SCHEDULED** orders (5 per page; the selection is per loaded page) and ≥1 vehicle, then **Plan Routes** | the button stays disabled until a vehicle is selected |
| 3 | In the dialog tick **Enable Pareto Analysis** and start the job | the hint "Runs 5 weighted scenarios and lets you switch between the best trade-offs" is visible |
| 4 | Watch the job-monitoring dialog while it solves | "Scenario k of 5 · Solving…" and a row of 5 dots filling in |
| 5 | After completion, reopen the Orders section (or open `http://localhost:4200/?solution=<solution id>`) | a 4th tab **Pareto** appears (only with candidates); without candidates it stays hidden |
| 6 | Open the Pareto tab | chart cost (x) vs CO₂ (y), frontier points joined in cost order, dominated points hollow, an active-plan ring and a recommended (★) marker; candidate list with metrics, weights, status and deltas vs the active plan |
| 7 | Select a dominated point, then a frontier point | the first shows a reason and **Apply disabled**; the second shows the comparison and **Apply enabled** |
| 8 | Click **Apply**, read the confirmation, then **Cancel** | nothing changes: no new revision |
| 9 | Click **Apply** and confirm | toast "Plan PARETO_Kx_OF_n applied (revision #N)", the active-plan line switches, the ring moves, a new `PARETO_ALTERNATIVE` revision exists and the map/route/timeline show its plan |
| 10 | Try to apply from a page showing an older revision | HTTP 409 and the banner "A newer revision exists. / Load latest revision"; the button reloads the chain head |

## Known limitations

- **Build is red for a pre-existing reason**: `login.component.scss` exceeds the 8 kB
  `anyComponentStyle` budget, so `npm run build` exits 1 on `4e2f928` with or without this work item
  (`angular.json` may not be changed). Same condition as DRIVER-DELIVERY-001.
- **Scenario dots and the Pareto note are unstyled** (plain unicode glyphs): the two `.scss` files
  were deliberately left untouched because they already exceed their component-style warning
  budget. Semantics and `title` text are correct.
- **Verification gaps:** no live run produced a `SERVICE_DOMINATED` candidate; no live clustered
  Pareto job (the clustering note is a problem approximation per AGENTS.md §9 and the UI copy says
  so); the `PARETO_ALTERNATIVE` → "Pareto plan" label in the send-route dialog was not exercised
  live; 422 (`NO_PLAN`, `ORDER_SET_DIFFERS`) and 400 `0040402` paths were not triggered live.
- **Apply is only possible on the latest revision** of a chain: any older base answers 409 with the
  recovery banner. Applying a plan does **not** change order statuses (by design).
- **No independent review yet**: the verification record was produced by the same orchestrator that
  dispatched the implementation. A `gvrp-reviewer` pass is recommended before acceptance.
- **Dev-database side effects** of the verification run were not rolled back (jobs #86/#87,
  solutions 65/66/67, and the orders served by those jobs are now `ON_ROUTE`).
- Two wording observations for the user/review, neither a proven defect: the "Active plan" label
  reflects the chain head even when an older revision is displayed; the dominated reason text says
  "another point is better on both cost and CO₂" also when the deltas are exactly 0.
