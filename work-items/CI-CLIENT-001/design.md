# Design — CI-CLIENT-001

## D1 Lock file (CCI-001)

- In `GVRP_Entry_client_v2`, run `npm install --package-lock-only --ignore-scripts`.
- Use Node 20 or 22 with npm 10, so the lock format matches CI (lockfileVersion 3).
- Expected diff:
  - adds `@jsverse/transloco` 7.5.1 and its dependencies;
  - flips `"dev": true` flags;
  - changes no other `version` field.
- If any other version changes, stop and report.
- `package.json` must not change.

## D2 Login stylesheet (CCI-002)

Rewrite `src/app/features/auth/login/login.component.scss` with the same visual result and a
smaller compiled size.

1. Remove the unused `@import` of `assets/styles/variables`.
2. Flatten the nesting.
   - Each element class becomes a top-level selector, for example `.feature-icon`, `.form-header h2`,
     `.form-group label`, `.form-group input[type="text"], .form-group input[type="password"]`,
     `.toggle-password`, `.btn-login`, `.btn-login .spinner`, `.divider`, `.btn-register`.
   - Keep one level of nesting only where a selector needs context to stay unambiguous: states
     (`&:hover`, `&:focus`, `&.error-border`, `&::before`), and selectors such as
     `.form-header p` versus `.feature-text p`.
3. Keep both media queries (`max-width: 968px`, `max-width: 480px`). Write their rules against
   `.login-container`, `.login-left`, `.login-right` and `.login-form-container` without the long
   ancestor chains.
4. Keep both keyframes, every colour, every `var(--…)` reference and every value. Do not change
   any declaration value.
5. Specificity check: flattening lowers specificity. For each rule whose selector got shorter,
   confirm that no global stylesheet (`src/styles.scss`, `src/assets/styles/*`) has a selector
   for the same element that could now win.
   - Highest risk: `input[type=…]`, `label`, `button`, `a`, `h2` and `p` global rules.
   - Where a global rule could win, keep one extra class for that rule (for example
     `.login-form input[type="text"]`).
   - List every such case in `implementation.md`.

Targets:

- The compiled size is below 8 kB, so the build has no error. Aim for 6 kB or less, to leave room.
- Below 4 kB (no warning) is welcome, but not required.

## D3 Verification of "visually unchanged" (CCI-003)

- Before editing, capture the login page at 1280×800, 900×800 and 400×800 (default, ≤968 px
  and ≤480 px layouts).
  - Also capture three states: the password field focused, a field showing the error border and
    error text, and the login button disabled.
  - Record the computed styles (`getComputedStyle`) of these elements: `.login-container`,
    `.login-left`, `.feature-icon`, `.form-header h2`, the username input (default and focused),
    `.toggle-password`, `.btn-login`, `.divider`, `.btn-register`.
- After editing, repeat the same captures and computed-style reads.
- The computed values must be identical. Report any difference with its cause; do not
  explain it away.

## Out of scope

- `angular.json` budgets.
- Other components' styles.
- The five known Karma failures.
- `.github/workflows/ci.yml`.
