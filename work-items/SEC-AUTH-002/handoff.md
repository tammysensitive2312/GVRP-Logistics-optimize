# Handoff — SEC-AUTH-002

Version: `ea49455` on `fix/sec-auth-002`; tested merged with the other two batch-2 items in `test/sec-batch2`.
Evidence: `verification.md` (automated checks by the implementing agent, runtime checks by Claude).

## User test steps

1. Sign in and use the app normally (orders, vehicles, plan a route). Expected: works as before.
2. `curl -i -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" -d "{}"`. Expected: 403 `0040301`.
3. Optional (needs a DB write you run yourself): sign in as a planner, run `UPDATE users SET branch_id=<other> WHERE id=<that user>`, reload any page. Expected: 401 / back to login. Restore the branch afterwards.

Claude ran the runtime checks listed in `verification.md` § Runtime on 2026-11-11.

## Known limitations and decisions

Decisions pending your acceptance (design.md): mismatched token → 401 (not a silent branch switch); a user loaded without a branch keeps the claim (DB has NOT NULL; the stricter rule needs a change to the existing `JwtAuthenticationFilterTest` fixture); register kept as a route that always answers 403. Test case `TC-D01-005` in `GVRP_Document/04-test-design/test-cases.csv` still expects 200 (change to 403 needs your approval).
