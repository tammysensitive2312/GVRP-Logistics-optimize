# DRIVER-MAP-003 — follow mode with an in-app permission gate

**Problem.** The position dot needed a manual "centre" tap, and asking for geolocation permission
through the browser prompt alone gave no explanation when the driver refused.

**Decision.**

- A follow-mode toggle keeps the map centred on the driver's position while the driver moves.
- Permission is requested through an in-app gate before the native browser prompt: the gate explains
  why the position is needed, offers to request it, and shows guidance when it is denied.
- Tracking starts only after the gate reports a granted permission; a denied state never retries in a
  loop.
- The toggle state is local to the screen and is not persisted.

**Date.** Requested 2026-10-09, accepted at the DRIVER-MAP-002 acceptance, closed after
DRIVER-MAP-003 handoff `0964372a…`; state `done` rev 10.

**Source.** `work-items/DRIVER-MAP-003/` (deleted): `design.md`, `verification.md`,
`user-feedback-01.md`, `closure.md` (FM-001…FM-004 verified by eight gate/follow specs plus a live
toggle check).

**Still valid in this documentation.** `03-basic-design/D06-dispatch/README.md`, `TC-D06-018`.

**Limitations recorded at closure.** The live "undecided" permission state was verified on a user
device, not by an agent run.
