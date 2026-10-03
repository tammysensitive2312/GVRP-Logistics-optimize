# Solution deep link — design (DEEP-LINK-001)

## Behavior

New public `SolutionLinkComponent` owns the exact `""` route (replacing the blind
`redirectTo: '/login'`, which dropped query params):

| Case | Behavior |
|---|---|
| No/invalid `?solution=` + authenticated | navigate `/main` (previous landing behavior preserved) |
| No/invalid `?solution=` + anonymous | navigate `/login` (previous behavior preserved) |
| Valid id + authenticated (DL-001) | `SolutionStore.loadById(id)` → success: toast + `/main`; failure: error toast + `/main` |
| Valid id + anonymous (DL-002) | `storage.updateAppState({lastUrl: '/?solution=<id>'})` → `/login`; post-login redirect returns here and loads (DL-003 fallback: load failure → error toast + `/main`) |

## Changes

1. New `src/app/features/solution-link/solution-link.component.{ts,spec.ts}` (no template file — empty template; navigates in `ngOnInit`).
2. `app.routes.ts`: `""` → `SolutionLinkComponent` (`pathMatch: 'full'`); `**` unchanged.
3. `login.component.ts:85`: `navigate([redirectUrl])` → `navigateByUrl(redirectUrl)` so a stored `"/?solution=47"` survives login (identical for plain paths like `/main`).
4. No guard, auth-service, or email changes (DL-004).

## Failure cases

- Non-numeric/negative id → treated as absent (DL-003).
- `loadById` error (404/500) → toast with server message, then `/main` (authenticated) — never a stuck blank page.
- Authenticated user without setup → existing `setupGuard` on `/main` applies as usual.

## Verification

- Component specs: all four cases (params via `ActivatedRoute` stub; spies for Router/AuthService/SolutionStore/StorageService/ToastService).
- `tsc` + route-view-unaffected full Karma suite (no regressions beyond the 5 pre-existing).
- Live browser check: `/?solution=47` anonymous → login → solution loads (during user test if ng serve picks it up).
