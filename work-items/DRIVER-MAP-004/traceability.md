# Traceability — DRIVER-MAP-004

| Req | Design | Code | Test |
|---|---|---|---|
| DM4-001 | design.md Behavior (guidance) | driver-view.component.{ts,html,scss}: `guidanceOpen`, `.guide-current`, `.guide-scroll` | spec: next-step card + toggle reveal |
| DM4-002 | design.md Behavior (stops) | same files: `stopsOpen`, `nextStop`, `#stopItem` template, `.next-stop` | spec: preview + toggle reveal |
| DM4-003 | design.md Failure cases; no service change | no backend/routing/follow edits | full suite green; prior driver specs pass |
| DM4-004 | design.md Verification | — | `bsk` live session (screenshots): collapsed cards + both toggles on route 30H-108480.LG |

Planned links above; code/test links verified at `implement`/`verify` nodes.
Parent: DRIVER-MAP-003 (follow-mode, done).
