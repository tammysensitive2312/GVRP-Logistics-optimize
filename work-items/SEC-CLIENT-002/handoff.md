# Handoff — SEC-CLIENT-002

Version: `ffb53a5` on `fix/sec-client-002`; tested merged with the other two batch-2 items in `test/sec-batch2`.
Evidence: `verification.md` (automated checks by the implementing agent, runtime checks by Claude).

## User test steps

1. As planner A, open a solution on the dashboard, then log out immediately.
2. Log in as planner B of another branch and open the dashboard.
Expected: no zone, route or stop of A on B's map; B's own solution still shows its zones and routes.

Claude ran the runtime checks listed in `verification.md` § Runtime on 2026-11-11.

## Known limitations and decisions

Decision: the reset is registered by MapService as a hook (SessionResetService does not import MapService) to keep Leaflet out of the initial bundle.
