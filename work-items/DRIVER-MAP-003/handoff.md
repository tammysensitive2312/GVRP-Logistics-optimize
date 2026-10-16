# Handoff — DRIVER-MAP-003 (awaiting user test)

Follow-mode for the driver map: toggle "📍 Follow my location" pans the map to the GPS dot on fixes; any manual drag/zoom pauses it; default OFF.

## Test steps (redeployed frontend; real phone best)

1. Driver opens a route → toggle shows "Follow my location".
2. Tap it → "Following on"; walk/drive → map follows the dot.
3. Drag the map manually → follow switches off by itself.
4. Toggle off → dot stays, map no longer follows.

## Automated verification

3 follow specs, full suite 160 + 5 pre-existing, tsc clean, budgets unchanged, live toggle verified by agent. Details: `verification.md`.

Reply with acceptance or rejection; silence is not acceptance.
