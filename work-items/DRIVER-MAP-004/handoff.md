# Handoff — DRIVER-MAP-004 (for user test)

Version: implement-side changes verified; run at `verify` → `await_user_test`.

## User test steps

1. Log in as driver (Branch Huge 1 / cuong).
2. Open route 30H-108480.LG.
3. Confirm: only the NEXT step card ("Head out… Step 1 of 82") and the
   NEXT STOP card show; buttons read "All 82 steps" and "Show all".
4. Tap both toggles: full step list (internally scrollable) and full stop
   list appear; tap again to collapse.
5. Confirm every stop still exposes phone/notes/Navigate link.

## Expected results

Collapsed-by-default cards; one-tap expand/collapse; no info removed;
map + follow behavior unchanged.

## Known limitations

"Next" is static (first step/stop); no GPS-based auto-advance in this run.
