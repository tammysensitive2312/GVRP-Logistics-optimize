# Request — CI-CLIENT-001

Date: 2026-10-19 00:35 (+07:00). Requested by the user in the Claude session:
"`login.component.scss`: làm gọn file xuống dưới 8 kB. tạo work item CI-CLIENT-001."

Make the `Client v2 (build)` CI job green:

1. `npm ci` fails because `GVRP_Entry_client_v2/package-lock.json` is out of sync with
   `package.json`: `@jsverse/transloco` is declared but missing from the lock.
2. `npm run build` fails because `login.component.scss` compiles to 9.40 kB, over the 8 kB
   `anyComponentStyle` error budget in `angular.json`.

Constraints:
- Keep the login screen visually the same.
- Do not raise the budget in `angular.json`.
- No dependency version changes.
