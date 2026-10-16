# Implementation — DRIVER-MAP-003

- `DriverViewComponent`: `following` signal (default false, reset on back) + `followGate` (`none`/`prompt`/`help`).
- `toggleFollow()`: on → async permission flow (granted → tracking on; denied → help panel; prompt/unknown → in-app explainer); off → plain off.
- `allowFollow()` → `getCurrentPosition` (native prompt): success → tracking on; refusal → help panel with re-enable guidance. `dismissGate()` closes without tracking.
- `map.on('dragstart zoomstart')` pauses follow one-way; `panTo(at, {animate:true})` on fixes while following.
- Template: map-bar toggle with `aria-pressed`, explainer + help panels. SCSS pressed state + gate panel.
- Tests: 8 specs (default/pan/pause + granted/denied/prompt/allow/refuse/dismiss). Full suite 165 + same 5 pre-existing; tsc 0; budgets unchanged.
- No backend/billing/history (FM-004).
