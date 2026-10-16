# Request — BULK-EDIT-001 (Phase 1: business analysis only for now)

Source: user message 2026-10-11 (Vietnamese, build mode) — build bulk edit orders status; user also reports UI filters appear to do nothing.

## Goal (preliminary, to be confirmed in Phase 1)

Dispatcher selects multiple orders and updates their status in one action, respecting the guarded order-status transitions.

## Authorization boundary

- Phase 1: read-only analysis + browser check + business discussion. No product implementation until the user explicitly accepts requirements.
- Vietnamese communication; English code/docs/artifacts. Follow AGENTS.md + business-development workflow; record every transition.
