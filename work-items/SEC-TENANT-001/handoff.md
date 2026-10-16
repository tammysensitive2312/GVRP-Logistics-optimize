# Handoff — SEC-TENANT-001 (for user test)

Version: branch `fix/sec-tenant-001`, uncommitted changes listed in
`implementation.md`. Run moves to `await_user_test` when the `pass` transition
is recorded; the handoff hash is then in `run.json`.

## User test steps

Preconditions: Entry API running on :8080 with the changed code; MySQL running.

1. Find one solution of your branch and one of another branch:
   ```sql
   SELECT s.id, s.branch_id, b.name FROM solutions s JOIN branches b ON b.id = s.branch_id ORDER BY s.id DESC LIMIT 20;
   ```
   If only one branch has data, create a second branch with one optimized plan
   first (test data only).
2. Log in as a planner of branch A and copy `access_token`:
   ```powershell
   $login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login -ContentType 'application/json' -Body '{"branch_name":"<branch A>","username":"<planner>","password":"<password>"}'
   $h = @{ Authorization = "Bearer $($login.access_token)" }
   ```
3. Own solution: `Invoke-RestMethod -Uri http://localhost:8080/api/v1/solutions/<id of A> -Headers $h` returns the solution.
4. Other branch: `Invoke-WebRequest -Uri http://localhost:8080/api/v1/solutions/<id of B> -Headers $h` fails with HTTP 400 and error code `0040402`.
5. Repeat step 4 for `/revision-chain` and `/latest-revision` of the same ID.
6. In the web client as branch A, open, edit and save a plan of branch A: it still works.

## Expected results

- Steps 3 and 6 behave as before.
- Steps 4 and 5 return the same error as a non-existent ID (try `/solutions/999999` to compare).

## Known limitations

- Role checks (a DRIVER calling planner endpoints) are not part of this run (SEC-006).
- The leaked JWT key (SEC-001) still allows forged tokens for any branch until rotated.
