# D09 — Solution deep link

The link in the job-completion e-mail opens the finished solution in the planner screen, even when the
reader is not logged in yet. The domain is client-only: it reuses `GET /api/v1/solutions/{id}` (D05).

Screens: `/` (exact empty path, `?solution=<id>`).
Diagram: [sequence-deep-link.svg](diagrams/sequence-deep-link.svg) ([source](diagrams/sequence-deep-link.puml)).

Use cases: UC-D09-01. Rules: BR-090 (the solution read stays branch-scoped).

---

## 1. Screens

| Route | Component | Purpose |
|---|---|---|
| `''` (`pathMatch: 'full'`) | `SolutionLinkComponent` | no UI; resolves `?solution=` in `ngOnInit` and navigates away |

The link is built by `EmailService` as `<frontend url>?solution=<solution id>` (the base URL comes from
configuration; documented by name only).

## 2. API

None of its own. A valid link calls `SolutionStore.loadById(id)`, which reads `GET /api/v1/solutions/{id}`.

## 3. Behaviour

| Case | Result |
|---|---|
| no `solution` parameter, or not a positive integer, and authenticated | navigate to `/main` |
| same, anonymous | navigate to `/login` |
| valid id, anonymous | store `lastUrl = "/?solution=<id>"` in the app state, navigate to `/login`; after login the client returns to that URL (`navigateByUrl`) and the case below applies |
| valid id, authenticated, load succeeds | toast *Solution loaded! Switch between tabs to view it*, navigate to `/main` with the solution active |
| valid id, authenticated, load fails (foreign branch, unknown id, server error) | error toast with the server message (or *Failed to load solution*), navigate to `/main` |

A solution of another branch is refused by the server (BR-090), so the link never shows another branch's
data; the user only sees an error toast.

## 4. Data touched

None on the server beyond the D05 read. Client state: `StorageService` app state (`lastUrl`) and
`SolutionStore`.

## Sources

`GVRP_Entry_client_v2/src/app/features/solution-link/solution-link.component.ts`,
`GVRP_Entry_client_v2/src/app/app.routes.ts`, `GVRP_Entry_client_v2/src/app/features/auth/login/login.component.ts`,
`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/service/EmailService.java`,
`_history/decisions/` (deep-link decision migrated from the closed work items).
