# Context — CI-CLIENT-001

Facts were checked by Claude on 2026-10-18/19 in a clean clone of `master` (`e98ec95`, plus a
later read of the working tree). Status labels follow AGENTS.md §5.

## C1 Lock file out of sync — VERIFIED

- CI `client-build` (`.github/workflows/ci.yml`): Node 20, `npm ci`, then `npm run build`.
- `package.json` declares `"@jsverse/transloco": "7.5.1"`, which the lock does not contain, so
  `npm ci` aborts with "package.json and package-lock.json are not in sync".
- `npm install --package-lock-only` in the clone produced a diff (kept by Claude as
  `package-lock.fix.diff` in its scratchpad, not in the repo) that:
  - adds the `@jsverse/transloco` 7.5.1 subtree;
  - removes 87 `"dev": true` flags on packages that are now also runtime-reachable;
  - changes no other version.
- With that lock, `npm ci` passed in the clone.

## C2 Login style budget — VERIFIED

- `angular.json` budgets: `anyComponentStyle` warning 4 kB, error 8 kB. Initial bundle warning
  500 kB, error 1 MB.
- `src/app/features/auth/login/login.component.scss`: 9,413 B of source with LF line endings
  (about 9.8 kB with CRLF on Windows).
  - Sass `--style=compressed` output: 9,429 B, which matches the build's "9.40 kB" error.
- Cause: deep nesting.
  - The prefix `.login-container .login-right .login-form-container` is repeated 44 times in
    the compiled CSS, about 2.3 kB.
  - `.login-form .form-group …` adds similar chains.
  - The component uses emulated encapsulation, so the ancestor chains are not needed for
    scoping.
- `@import '../../../../assets/styles/variables'` emits no CSS: the partial has only `$`
  variables and a mixin, and the file uses `var(--…)` custom properties. The import is unused.
  `@import` is also deprecated in Dart Sass.
- Spec: `login.component.spec.ts` exists and is in the snapshot of protected specs. It tests
  behaviour, not styles.

## C3 Other build warnings — VERIFIED (out of scope)

Nine other component styles exceed the 4 kB warning, and the initial bundle (about 665 kB)
exceeds the 500 kB warning. These are warnings only, so they do not fail the build. They are
not part of this item.

## C4 Unit tests — VERIFIED

Karma has five known pre-existing failures:

- 3× CollectionCache
- FleetSetupComponent "keeps the vehicle when the confirmation is dismissed"
- NavbarComponent "navigates when a tab is clicked"

`client-test` is `continue-on-error`. Fixing these failures is not part of this item.
