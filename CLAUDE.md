# Claude Code instructions — GVRP Logistics Optimize

The project constitution lives in AGENTS.md and applies in full:

@AGENTS.md

The notes below only add Claude Code specifics. If they ever conflict with
AGENTS.md, AGENTS.md wins and the conflict must be reported to the user.

## Session start

Follow AGENTS.md §16: start by running `node tools/session-brief.mjs`
(read-only). Claude may have no shell on the user's machine; then read the
same sources directly (`work-items/*/run.json`, the security checklist,
`docs/engineering/LESSONS.md`) or ask the engineer agent for the output.

For research tasks, read `docs/research/PROJECT_STATE.md` before acting
(AGENTS.md §7). Durable conclusions go into `docs/research/*` files, not
into chat or auto memory — auto memory is a convenience, not project truth.

## Database access (read-only)

- Preferred: the `dbhub` MCP server from `.mcp.json` (tool `execute_sql`,
  MySQL `gvrp_db` on localhost:3306, user `agent_reader`, SELECT/SHOW VIEW
  only, max 500 rows). Password comes from the `GVRP_DB_PASSWORD`
  environment variable; never write it into any file or message.
- The MySQL server runs in a local Docker container. If dbhub is not
  available, ask the user before using any other DB access path.
- Never run INSERT/UPDATE/DELETE/DDL against `gvrp_db`. Data changes go
  through the application or are proposed to the user as SQL to run.
- Large result sets: aggregate in SQL or export to a file and analyse in
  code; do not page hundreds of rows through the conversation.

## Build and test

| Module | Command (run inside the module directory) |
|---|---|
| GVRP_Engine_API | `mvnw.cmd test` (single test: `mvnw.cmd -Dtest=ClassName test`) |
| GVRP_Entry_API | `mvnw.cmd test` |
| GVRP_Entry_client_v2 | `npm test`, `npm run build` |
| tools | `node --test tools/feature-workflow/workflow.test.mjs tools/session-brief.test.mjs`, `python tools/experiment_validator.py` |

Ports: Entry API 8080, Engine API 8081, MySQL 3306.

## Secrets

`application.properties` files contain credentials. Do not print, copy,
or commit their secret values; refer to them by property name only.
These credentials are already in git history (SEC-001 in
`docs/security/SECURITY_CHECKLIST.md`).

## Security and tests

AGENTS.md section 14 (Test Integrity) and section 15 (Security and Tenant
Isolation) apply to every code change. Each branch is a separate company:
scope branch-owned data to the caller's branch and test cross-branch refusal.
CI is defined in `.github/workflows/ci.yml`; run `/security-review` on
changes touching authentication, endpoints or queries on branch-owned data.
