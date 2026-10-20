# Design — PARETO-002

Visual reference: Claude design canvas "GVRP Pareto UI" (2026-10-18; artboards: Pareto tab
overview with interactive selection and apply confirmation, selection-panel states incl.
409 banner, job monitoring with scenario progress, planning dialog Pareto option). Sample
data only.
UI text is English, matching the existing app.

## 1. Data layer

`C/core/models/pareto.model.ts` (export from `models/index.ts`):

```ts
export type FrontierStatus = 'FRONTIER' | 'DOMINATED' | 'SERVICE_DOMINATED';
export type NotApplicableReason = 'NOT_ON_FRONTIER' | 'NO_PLAN' | 'ALREADY_ACTIVE' | 'ORDER_SET_DIFFERS';

export interface ParetoCandidateDTO {
  id: number; index: number; label: string;
  cost_weight: number; co2_weight: number;
  total_cost: number; total_distance: number; total_co2: number; total_time: number;
  total_vehicles_used: number; served_orders: number; unserved_orders: number;
  frontier_status: FrontierStatus; engine_selected: boolean;
  applicable: boolean; not_applicable_reason: NotApplicableReason | null;
}

export interface ParetoFrontierDTO {
  job_id: number | null; solution_id: number; latest_solution_id: number;
  active_candidate_id: number | null; clustered: boolean; cluster_count: number | null;
  candidates: ParetoCandidateDTO[];
}
```

`SolutionDTO` gains `pareto_candidate_id?: number | null` and `type?: string`.
`JobProgressDTO` gains `scenario?: number; scenarioCount?: number`.
`ApiService`: `getPareto(solutionId)` -> GET `/solutions/{id}/pareto`;
`applyParetoCandidate(solutionId, candidateId)` -> POST
`/solutions/{id}/pareto/{candidateId}/apply` (empty body) -> `SolutionDTO`.

## 2. Pure utilities `C/shared/utils/pareto-chart.utils.ts`

- `niceTicks(min, max, count = 4): number[]`.
- `scaleLinear(domain: [number, number], range: [number, number])` -> function; a zero-width
  domain maps everything to the range midpoint; domain padded 8 % each side by the caller.
- `frontierPath(points)`: FRONTIER points sorted by cost ascending -> SVG `d` string.
- `compactVnd(value)`: `1 234 567` -> `1.23M`, `845 000` -> `845K` (axis labels only;
  tables keep the full `formatVndAmount`).
- `deltaPct(value, base)`: `null` when base is 0.

## 3. Pareto tab

Orders section: 4th `mat-tab` "Pareto" (icon `insights`), rendered with `@if` only when
`paretoState().candidates.length > 0`. `OrdersSectionComponent` loads the frontier
whenever `solution()` changes (effect + `queueMicrotask`, same pattern as `RouteViewComponent`
assignees); a failed load hides the tab silently and logs once. Call `getPareto` **only when
`solution()` is non-null** and clear the state when it becomes null — the existing
`orders-section.component.spec.ts` mocks `ApiService` without `getPareto` and runs with a null
solution, so it must not reach the new call. When the active solution changes to an id
already shown, do not refetch.

New component `C/features/main/orders/pareto-view/pareto-view.component.{ts,html,scss}`,
inputs `solution: SolutionDTO`, `frontier: ParetoFrontierDTO`; output `reload`.

Layout top to bottom (panel width 380–640 px, so stack, never side-by-side):

1. **Summary strip** — "5 scenarios · 3 on the frontier · recommended K2". Chip
   `Clustered · 7 clusters` (info icon, tooltip: "Computed on the clustered problem. Each
   vehicle is limited to one area for all scenarios, so the CO₂ range may be narrower than
   without clustering.") or chip `Full problem`.
2. **Active plan line** — "Active plan: K2 · cost-weight 0.25 / CO₂-weight 0.75" or, when
   the latest revision has no candidate, "Active plan: manual edit (rev #45) — Pareto
   points can still be applied when they cover the same orders".
3. **Chart** (inline SVG, `viewBox="0 0 560 300"`, responsive width):
   - x = total cost (VND, compact ticks), y = CO₂ (kg); light grid on ticks; axis titles.
   - frontier: line through FRONTIER points (cost order), filled circles r = 6.
   - DOMINATED: hollow circle r = 6. SERVICE_DOMINATED: small "×" + dashed grey.
   - active point: extra outer ring r = 11. Engine-recommended: "★" label above.
   - selected (focused) candidate: label "K1" bold and the row highlighted.
   - each point is a `<g role="button" tabindex="0">` with `aria-label`
     ("K1, cost 1.21M VND, CO₂ 12.4 kg, frontier"); Enter/Space selects; native
     `<title>` as tooltip.
   - Legend under the chart, shapes + words (not colour only).
   - Arrow hint in the corner: "↙ better" (both objectives are minimised).
4. **Candidate list** — one row per candidate in index order (K0 = most CO₂-weighted …
   K4 = cost only). Row: label + a 2-segment weight bar (cost vs CO₂); cost; CO₂; km;
   vehicles; served (`118/120` warning colour if < max); status chip; delta vs active
   (`+4.2 % cost · −11.3 % CO₂`, green for decrease, red for increase, with sign text).
   Row click selects.
5. **Selection panel** (when a candidate is selected and it is not active): comparison
   grid vs active (cost Δ VND and %, CO₂ Δ kg and %, km Δ, vehicles Δ, time Δ) and the
   button **Apply this plan**. Disabled states show the reason inline:
   `ALREADY_ACTIVE` "This is the active plan", `NOT_ON_FRONTIER` "Dominated — another point
   is better on both cost and CO₂" / for SERVICE_DOMINATED "Serves fewer orders",
   `ORDER_SET_DIFFERS` "Covers a different set of orders than the active plan",
   `NO_PLAN` "No stored routes for this point".

### Apply flow
1. `ConfirmService.ask({ title: 'Apply plan K1?', message: 'A new revision is created from
   this plan. The current plan stays in the revision history. Routes already sent to
   drivers must be sent again.', confirmText: 'Apply', cancelText: 'Cancel' })`.
2. `api.applyParetoCandidate(solution().id, candidate.id)`; button shows a spinner;
   double submit blocked.
3. Success: `SolutionStore.setSolution(saved)`; emit `reload` (section refetches the
   frontier for the new id); toast "Plan K1 applied (revision #N)".
   Not-found (foreign or missing id) arrives as HTTP 400 with `errors[0].code = "0040402"`
   (repo-wide `GlobalExceptionHandler` mapping, see PARETO-001 review F3): hide the tab /
   toast "Plan not found".
4. 409: inline banner "A newer revision exists." + button **Load latest revision**
   (`api.latestRevisionId` -> `store.loadById`). 422: toast with
   `error.errors[0].message`. Network/other: toast "Could not apply the plan."

## 4. Job monitoring and planning dialog

- Monitoring: when `progressInfo().scenarioCount > 0`, the hint reads
  "Scenario {{scenario}} of {{scenarioCount}} · Solving…" and a small row of n dots shows
  done / current / pending scenarios. The percent already comes overall from the engine.
- Monitoring result: after the solution loads, call `getPareto(solution.id)`; when
  candidates exist show one line "Pareto: 3 plans on the frontier — open the Pareto tab to
  compare." (no new cards).
- Planning dialog: when `enablePareto` is checked, the estimated time line becomes
  "{{estimate}} × 5 scenarios (Pareto)" and the hint "Runs 5 weighted scenarios and lets you
  switch between the best trade-offs."

## 5. Send dialog
In `send-route-dialog.component.html` the option text is
`Solution #{{ revision.id }} ({{ revision.type }}, {{ revision.status }})`. Show
"Pareto plan" instead of the raw type **only** for `PARETO_ALTERNATIVE` (small component
method `typeLabel(type)`); every other type keeps its raw text so existing specs stay valid.

## 6. Tests (new specs only)

| # | Spec | Expected |
|---|---|---|
| U-T1 | `pareto-chart.utils.spec.ts` | ticks, zero-width domain midpoint, frontier path order, compactVnd, deltaPct(base 0) = null |
| U-T2 | `pareto-view.component.spec.ts` render | 5 point groups; 3 filled, 1 hollow, 1 cross; active ring on the active id; ★ on engine-selected |
| U-T3 | selection | clicking a row selects; Apply disabled with the reason text for each `not_applicable_reason` |
| U-T4 | apply success | confirm true -> API called with (solution.id, candidate.id) -> store.setSolution called -> reload emitted |
| U-T5 | apply 409 / 422 | banner + load-latest button / toast with server message; store untouched |
| U-T6 | orders section | Pareto tab absent for empty candidates, present otherwise |
| U-T7 | monitoring | "Scenario 3 of 5" rendered when progress has scenario keys; absent otherwise |
| U-T8 | send dialog (new spec file or new `it` in a new file) | `PARETO_ALTERNATIVE` shows "Pareto plan"; `ENGINE_GENERATED` unchanged |

Existing specs unchanged; `npm test` and `npm run build` green.
