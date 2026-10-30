# DRIVER-MAP-004 — progressive disclosure of the driver route

**Problem.** On a phone the driver screen showed every stop in full, so the current stop was pushed
off screen by the rest of the list.

**Decision.**

- The driver route is rendered as expandable sections: the current stop is expanded, later stops are
  collapsed to a one-line summary, and completed stops stay available for review.
- The "next" step is the first item in the remaining list. It is a static rule: there is no
  GPS-based auto-advance, and a stop does not advance by itself.
- Expanding a section does not change the send or any delivery state; the driver view stays read-only.

**Date.** Requested 2026-10-12; accepted 2026-10-23 on the current code; state `done` rev 11.

**Source.** `work-items/DRIVER-MAP-004/` (deleted): `design.md`, `verification.md`,
`verification-rereview.md`, `findings-scope-drift.md`, `closure.md`.

**Still valid in this documentation.** `03-basic-design/D06-dispatch/README.md`, `TC-D06-019`.

**Limitations recorded at closure.** Earlier acceptance (2026-10-12) could not be used because the
scope moved afterwards; the run was re-accepted on the current version.
