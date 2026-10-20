# Traceability — PARETO-002

Status after the verify node (2026-10-18): unit/compile evidence from the 6 new spec files plus
the live browser run recorded in `implementation.md` §Live End-to-End Browser Verification.

| Req | Design | Code (inspected) | Test | Status |
|---|---|---|---|---|
| PUI-001 | §3 tab | `orders-section.component.{ts,html}`, `pareto-view/*` | U-T6 | VERIFIED (unit + live: tab appears only with candidates) |
| PUI-002 | §2, §3.3 chart | `shared/utils/pareto-chart.utils.ts`, `pareto-view.component.html` | U-T1, U-T2 | VERIFIED_WITH_LIMITATION — live: frontier path excludes the dominated point, active ring, engine star, ticks/legend/titles, DB-matching deltas; the `SERVICE_DOMINATED` marker was **not** produced by any live run |
| PUI-003 | §3.4 list | `pareto-view.component.*` | U-T2, U-T3 | VERIFIED (unit + live deltas recomputed against DB) |
| PUI-004 | §3.5 selection panel | `pareto-view.component.*` | U-T3 | VERIFIED (unit + live: reason text, Apply disabled for dominated, enabled for frontier, disabled for the active candidate) |
| PUI-005 | §3 apply flow | `pareto-view.component.ts`, `api.service.ts` | U-T4, U-T5 | VERIFIED (unit + live: confirm/cancel with no mutation, 200 → revision #67, 409 → banner, "Load latest revision" → reload of the head) |
| PUI-006 | §3.1 clustered note | `pareto-view.component.html` | U-T2 | PARTIALLY_VERIFIED — the note and cluster count are unit-tested and bound to the API field, but no live run was clustered, so the chip was never seen with real data |
| PUI-007 | §4 monitoring + dialog | `job-monitoring-dialog.*`, `route-planning-dialog.*`, `job.model.ts` | U-T7 | VERIFIED_WITH_LIMITATION — live: "Scenario k of 5 · Solving…" and the dot states on a real job; the planning-dialog "about n times longer" estimate line was not read live |
| PUI-008 | §5 send dialog label | `send-route-dialog.component.{ts,html}` | U-T8 | PARTIALLY_VERIFIED — unit-tested only; the send-route picker was not opened in the live run |
| PUI-009 | §6 tests | no new npm dependency; new specs only | full Karma run + `npm run build`; byte comparison of 22 snapshotted spec/testing files | VERIFIED_WITH_LIMITATION — 220 specs (215 pass, 5 pre-existing failures), no spec/testing file touched, no new dependency; `npm run build` still exits 1 on the **pre-existing** `login.component.scss` budget error (deviation 1) |

API under test: PARETO-001 (merged to master `4e2f928`).

No live run produced a service-dominated candidate, so the "× Serves fewer orders" marker, chip and
reason text rest on unit evidence only. The clustered path (PARETO-001 clustering) likewise remains
unverified with real data — per AGENTS.md §9 it is a problem approximation, and the UI copy says so.
