# Verification — DRIVER-MAP-004: re-verification on current tree (2026-10-23)

Environment: Windows 11; JDK via mvnw; Node v22; Chrome Headless 155. Scope has no uncommitted changes.

## Checks run (2026-10-23)

- Client v2 `npx ng test --watch=false --browsers=ChromeHeadless` (CI command): **245/245 SUCCESS**, exit 0.
  The 5 failures recorded as pre-existing at handoff (FleetSetup, Navbar, 3x CollectionCache) no longer occur (fixed by `5cd1ae8`).
  Includes driver specs: `driver-view.component.spec.ts` (24 cases), `driver.guard.spec.ts` (3), `send-route-dialog*.spec.ts`, `route-view.component.spec.ts`, `login.component.spec.ts`.
- Client v2 `npm run build`: exit 0, no ERROR. WARNINGs only: initial bundle budget (665.58 kB vs 512 kB), several component SCSS budgets, `leaflet` CommonJS module (used by driver-view).

## Requirement coverage

DM4-001..DM4-004: suites green on the current tree. Live behaviour: the user stated on 2026-10-23 that they
tested this feature on the current code and it meets requirements (user test, not an agent check).

## Not executed

- No agent-driven `bsk` browser walkthrough this round: the local stack was not running (MySQL down).
  Live coverage relies on the user's own test above and the earlier live evidence in `verification.md`.
- Real-device GPS limitations from earlier rounds still apply where they applied.

No open defects in scope → `pass`.
