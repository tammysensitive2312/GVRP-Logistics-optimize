# CI-CLIENT-001 — client v2 in continuous integration

**Problem.** CI built and tested only the Java modules. A client change could break the Angular
build without any signal, and `npm ci` failed in CI because `package-lock.json` was not tracked.

**Decision.**

- Add a client build job and a client test job to the GitHub Actions workflow, running
  `npm ci`, `npm run build` and the Karma suite.
- `client-test` keeps `continue-on-error` while five pre-existing client spec failures remain; the
  failures are a separate task, not a reason to hide the job.
- Keep the component style budget as configured. The login component style sits at 4.10 kB against
  the 8 kB budget; the residual budget warning there is accepted, not suppressed.
- `npm ci` requires the Angular dev server on port 4200 to be stopped first, otherwise the install
  fails on locked files.

**Date.** Accepted 2026-10-20 07:31 (+07:00), handoff `e60c55f4…`; state `done` rev 8.

**Source.** `work-items/CI-CLIENT-001/` (deleted): `design.md`, `review.md` (PASS with findings
C1–C5), `implementation.md`, `closure.md`. Workflow file: `.github/workflows/` (unchanged by this
documentation task).

**Still valid in this documentation.** `04-test-design/01-test-strategy.md` — the `CLIENT` level and
the note that five client specs were already failing before this rebuild.

**Open follow-ups recorded at closure.** Confirm the client build job is green after the push; fix
the five pre-existing Karma failures with code fixes (never by editing the specs); then consider
removing `continue-on-error`.
