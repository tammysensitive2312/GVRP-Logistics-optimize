# Handoff — PARETO-001

- Version: branch `feature/pareto-001` (base `e98ec95`), working tree as reviewed on
  2026-10-18; ledger transition `pass` (verify → await_user_test) recorded by `close-run.ps1`.
- User test performed: manual API test on the local stack, reported successful on
  2026-10-18 18:03 (+07:00).
- Deployment step: run `GVRP_Entry_API/src/main/resources/database/migrations/2026-10-18-pareto-candidates.sql`
  **before** starting Entry (review F2).

## Known limitations

- Not-found answers HTTP 400 with code `0040402` (repo-wide handler), not 404.
- Applying a point is limited to plans covering the same order set as the active plan.
- Clustered Pareto is a problem approximation; the vehicle-to-cluster assignment ignores
  emission factors, so the CO2 range may be narrower than the dense problem. Not verified.
- `ParetoCandidate` entity lacks the unique `(job_id, candidate_index)` declaration (SQL has it).
- Three heavy GraphHopper tests were not run in this work item.
- Client display is PARETO-002.
