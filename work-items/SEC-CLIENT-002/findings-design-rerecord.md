# Findings — SEC-CLIENT-002 (design re-record)

Raised by Claude (parent) on 2026-11-10 during review, before the handoff.

`design.md` and `traceability.md` were edited after the design step (revision 2): decision D2 was added
when the first implementation (MapService imported by SessionResetService) pulled Leaflet into the
initial bundle (670.96 → 847.35 kB) and the dependency was reversed to a registered reset hook. The
ledger therefore reports both files as stale evidence. No code change follows from this finding; the
run goes back to `design` only to record the current design and traceability, then forward again.
