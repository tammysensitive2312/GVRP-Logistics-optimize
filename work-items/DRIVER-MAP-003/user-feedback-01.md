# User feedback — DRIVER-MAP-003 (2026-10-09)

Source: user message requesting an in-app location-permission step on follow toggle.

- Current: `watchPosition` starts at detail open (dot feature); the browser's native prompt appears unannounced, and the toggle has no explanation/denied-recovery path.
- Requested: enabling follow shows an in-app permission explainer first; on approval, tracking behaves like Google Maps (dot + follow); on denial, guidance to re-enable instead of a dead end.
- Accepted as compatible UX addition (no requirement change to FM-001..004; adds an explicit permission gate + denied-recovery panel).
