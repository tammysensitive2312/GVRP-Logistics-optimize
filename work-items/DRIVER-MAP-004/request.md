# Request — DRIVER-MAP-004 (driver progressive disclosure)

Source: user message 2026-10-12 (build mode). Parent: DRIVER-MAP-003 (done).
Credentials provided by user for live checks — PLANNER: Branch Huge 1/hien/hien;
DRIVER: Branch Huge 1/cuong/cuong (used only for browser testing, never stored in code).

## Goal

Driver route detail dumps all maneuver guidance (23+ steps live) and the full
stop list at once. Show the two progressively, Google-Maps style: a "next"
card up front, full lists one tap away.

## Requirements (user-approved inline)

- DM4-001: guidance section shows the next step card (instruction + distance +
  "Step 1 of N") with an "All N steps" toggle; full list collapsed by default,
  scroll-bounded when open.
- DM4-002: stop section shows a next-stop card (first non-depot stop with full
  meta + Navigate link) with a "Show all"/"Hide all" toggle; full list
  collapsed by default.
- DM4-003: no backend, no routing logic, no follow-mode behavior change; all
  existing stop info stays reachable.
- DM4-004: suites green; live `bsk` check as driver on a real route.

## Exclusions

Step tracking / auto-advance of the "next" card (static first step/stop;
a future run can advance it from GPS progress).
