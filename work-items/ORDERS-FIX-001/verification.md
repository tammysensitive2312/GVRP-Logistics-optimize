# Verification — ORDERS-FIX-001

Verifier: Claude Code, 2026-10-31, Windows host. Version: branch `fix/orders-fix-001` (from `master` @ `081640a`), working tree after the rework (`implementation-rework.md`).

## Commands and outcomes

| Check | Command | Outcome |
|---|---|---|
| Entry unit and web tests | `.\mvnw.cmd -B clean test` (PowerShell, `JAVA_HOME` = JDK 21) | EXIT 0, 324 tests, 0 failures, 0 errors, 0 skipped |
| Client specs | `npx ng test --watch=false --browsers=ChromeHeadless` | EXIT 0, 446 SUCCESS (baseline 435) |
| Client build | `rm -rf dist && npm run build` | EXIT 0, 0 ERROR, 12 warnings (unchanged set) |
| Encoding | code-point `Select-String` scan of changed files | 0 hits; positive control 1 hit |
| Scope | `git status --short` | only the files allowed by `task-claude-code.md` |

## Requirement coverage

| Req | Design | Automated evidence |
|---|---|---|
| OF-R1 ungeocodable import row not saved | S1 | `OrderImportRowsTest.ofT1_*`, `ofT2_*` |
| OF-R2 optional fields clearable | S2 | `OrderMapperSetToNullTest` |
| OF-R3 address change relocates | C1 | `order-payload-stale.utils.spec.ts`, `edit-order-dialog-relocate.component.spec.ts` (OF-C2) |
| OF-R4 source line numbers | S3 | `OrderImportRowsTest.ofT4_*`, `ofT5_*`, `ofT6_*`, `ofT7_databaseFailure*` |
| OF-R5 no personal data in logs / errors | S4 | `OrderImportRowsTest.ofT7_*` |
| OF-R6 single time-window bound refused | S5, C2 | `OrderServiceTimeWindowTest`, `edit-order-dialog-relocate.component.spec.ts` (OF-C3) |

## Limitations

- UI-facing change (edit dialog hint and validation): no browser session was run (`bsk` not used). The behaviour is covered by component specs only.
- Manual scenarios OF-M1…OF-M6 (`review.md`), including live geocoding and a real database failure during import: NOT_VERIFIED.
- A database failure of one import row still marks the whole import transaction rollback-only (design §4, excluded by the request).

No blocking mismatch between design and code was found.
