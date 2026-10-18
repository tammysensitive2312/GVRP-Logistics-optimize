# Request — PARETO-002 (client v2: Pareto display and plan switching)

Source: same user message as PARETO-001 (2026-10-18 12:22 +07:00), part "thiết kế giao
diện hiển thị cho pareto", and the decisions recorded in
`work-items/PARETO-001/request.md` (view + switch, service first, clustering same as
single, two work items).

Depends on: PARETO-001 accepted and merged (API `GET /solutions/{id}/pareto`,
`POST /solutions/{id}/pareto/{candidateId}/apply`, progress keys `scenario`,
`scenarioCount`, `SolutionDetailResponseDTO.pareto_candidate_id`).

Status: design only. `task-opencode.md` is issued after PARETO-001 is accepted, so the
task can quote the merged API exactly.

## Requirements

- PUI-001: A "Pareto" tab in the orders section, shown only when the active solution's
  chain has Pareto candidates.
- PUI-002: Trade-off chart (cost on x, CO2 on y) of all candidates: frontier points joined
  in cost order, dominated points hollow, service-dominated points marked separately, the
  active point and the engine-recommended point distinguishable without colour alone.
- PUI-003: Candidate list with metrics (cost, CO2, km, vehicles, served/unserved), the
  weights used, status, and the difference to the active plan.
- PUI-004: Selecting a candidate shows its comparison to the active plan and an "Apply"
  action, enabled only when the API says `applicable`; otherwise the reason is shown.
- PUI-005: Apply asks for confirmation, calls the apply endpoint with the displayed
  solution id, sets the returned revision as the active solution (map, route and timeline
  views update), and refreshes the Pareto data. 409 offers "Load latest revision"; 422 shows
  the server message.
- PUI-006: Clustered runs show a visible note that the frontier is computed on the
  clustered problem (approximation), with the cluster count.
- PUI-007: Job monitoring shows "Scenario k of n" while a Pareto job solves; the route
  planning dialog warns that Pareto takes about n times longer.
- PUI-008: The send-route revision picker labels `PARETO_ALTERNATIVE` revisions.
- PUI-009: No new npm dependency (chart drawn with inline SVG); unit specs for the chart
  utilities and the component; existing specs unchanged and green; `npm run build` green.

## Exclusions

- Previewing a non-active candidate's routes on the map (needs a plan-read endpoint;
  candidate follow-up PARETO-003).
- Changing what the backend computes or stores.
