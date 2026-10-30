# 02 — Deployment

What the repository actually specifies about running the system, and what it leaves to an operator.
There is no deployment descriptor, no container image and no compose file, so this document separates
**what is defined** from **what has to be supplied**.

Diagram: [deployment.svg](diagrams/deployment.svg) ([source](diagrams/deployment.puml)).

---

## 1. What is defined in the repository

| Item | State | Source |
|---|---|---|
| Entry image | `GVRP_Entry_API/Dockerfile` exists and is **empty** (0 bytes). No build stage, no base image. | `GVRP_Entry_API/Dockerfile` |
| Engine image | none | repository inventory |
| Compose / orchestration | none | repository inventory |
| Kubernetes / Helm / Terraform | none | repository inventory |
| Reverse proxy or TLS terminator | none; assumed to exist in front of the deployment | `NOT_VERIFIED` — nothing in the repository configures HTTPS |
| CI | GitHub Actions, six jobs, no deploy step | `.github/workflows/ci.yml` |

The empty `Dockerfile` is worse than an absent one: it looks like containerisation exists. Any
`docker build` of `GVRP_Entry_API` fails, and nothing in the repository says how the two JARs are
meant to be produced or started.

## 2. Runtime prerequisites

### Software

| Requirement | Version used | Why |
|---|---|---|
| JDK | 21 (CI: Temurin 21; local baseline: 21.0.1) | Both services compile with `release 21`; the engine enables virtual threads |
| MySQL | 8 (schema uses `POINT`, `MULTIPOLYGON`, `JSON`, `ENUM`, SRID 4326, spatial indexes) | `ddl-auto=validate` refuses to start on a different schema |
| Maven wrapper | bundled (`mvnw`, `mvnw.cmd`) for both services | build and test |
| Node.js | 20 (CI) | client build and tests |
| Chrome / Chromium | headless | client unit tests (Karma) |

### Data and files that are not in git

| Artefact | Size | Needed by | Location |
|---|---|---|---|
| Vietnam OSM extract | hundreds of MB, gitignored | Engine at startup (GraphHopper import) | `GVRP_Engine_API/.data/vietnam-251213.osm.pbf` |
| Routing graph cache | large, gitignored | Engine at startup | `GVRP_Engine_API/.cache/routing-graph` |
| Result spool | grows with jobs | Engine at runtime | `GVRP_Engine_API/.data/results/{pending,sent,poison}` |
| Uploaded import files | per upload | Entry during order import | `upload.default.directory` is **commented out**, so imports are parsed in memory, not stored |
| Database schema | small | Entry and Engine-adjacent tooling | `GVRP_Entry_API/src/main/resources/database/**` |

The first engine start imports the OSM file and builds the graph; later starts reuse the cache. A
new machine with no cache takes minutes and a lot of RAM. Nothing in the repository documents the
required RAM.

### Network

| Direction | Purpose | Consequence if missing |
|---|---|---|
| Browser → Entry | all API calls | unusable system |
| Browser → `https://mt1.google.com/vt/...` | map tiles | maps render blank |
| Browser → `https://router.project-osrm.org` | fallback geometry | driver manoeuvres missing only when stored legs are incomplete |
| Entry → Engine | dispatch, progress, cancel, evaluate, legs | planning jobs fail with "Cannot connect to optimization engine…" |
| Engine → Entry | completion / failure / cancelled callbacks | results stay in `pending/` and retry for up to 20 attempts |
| Entry → MySQL | all persistence | application does not start |
| Entry → Goong | geocoding | orders without coordinates cannot be saved |
| Entry → SMTP | notifications | jobs complete, no mail |
| Entry → Slack webhook | completion notice | jobs complete, no Slack message |

The two internal directions are both needed: a host that can only be reached one way half works —
planning succeeds and results never come back.

## 3. Configuration surface

### Entry

| Property group | Purpose | Value in the repository |
|---|---|---|
| `spring.datasource.*` | MySQL connection | present, with credentials inline |
| `spring.jpa.hibernate.ddl-auto` | schema validation | `validate` |
| `spring.gvrp.jwt.secret-key`, `expiration-ms` | token signing and lifetime | present; 24 h lifetime |
| `spring.mail.*` | SMTP | present |
| `geocoding.goong.*`, `geocoding.enabled` | Geocoding provider | present |
| `spring.optimization.engine.url`, `engine.api.key` | Engine base URL and shared key | present |
| `engine.api.key` (inbound) | key Entry accepts on callbacks | present |
| `gvrp.cleanup.*` | retention sweep | `enabled=true`, **`dry-run=true`** |
| `spring.app.frontend.url` | base URL used in e-mail links | present |

Profiles: only `application-local.properties` exists, and it holds the same key set as the main
file. Nothing selects a profile — not the properties, not a descriptor — so `application-local` is
inert unless `--spring.profiles.active=local` is passed explicitly. There is no prod profile file:
a production deployment overrides values through environment variables or command-line arguments,
and no descriptor shows which ones it overrides.

Several secrets are stored as literal values in tracked property files. Property names are listed
here deliberately; the values are not reproduced. This is security finding SEC-001.

### Engine

| Property | Value |
|---|---|
| `server.port` | `8081` |
| `graphhopper.osm.file` | `./.data/vietnam-251213.osm.pbf` |
| `graphhopper.graph.location` | `./.cache/routing-graph` |
| `graph.encoded_values` | `road_access` |
| `spring.optimization.entry.url` | `http://localhost:8080/api/v1` |
| `entry.api-key` | shared key, inline value |
| `gvrp.result-spool.dir` | `./.data/results` |
| `gvrp.result-spool.max-attempts` | `20` |
| `gvrp.result-spool.retry.initial-delay-ms` / `interval-ms` | `60000` / `120000` |
| `gvrp.result-spool.{sent,poison}-retention-days` | `7` / `30` |
| `gvrp.result-spool.sweep-cron` | `0 0 3 * * *` |

The engine defaults to calling `localhost:8080`, so on a single host the out-of-the-box configuration
works; on two hosts both `spring.optimization.entry.url` (engine) and `spring.optimization.engine.url`
(Entry) must be set to real addresses.

## 4. Continuous integration

Six jobs, all on `ubuntu-latest`, triggered by push to `master`, any pull request, and manual dispatch.
Permissions are read-only and a concurrency group cancels superseded runs.

| Job | What it runs | Blocking |
|---|---|---|
| `secrets` | gitleaks on the commits of the push/PR (fetch-depth 0) | yes |
| `engine-api` | `mvnw verify` with eight test classes excluded | yes |
| `entry-api` | `mvnw verify` with two test classes excluded | yes |
| `client-build` | `npm ci` then `npm run build` | yes |
| `client-test` | Chrome sandbox sysctl, then `ng test --watch=false --browsers=ChromeHeadless` | yes (245/245 since 2026-10-23) |
| `tools` | `node --test` for the workflow and session-brief tools | yes |

There is no deploy job, no artifact upload and no environment protection. CI proves the code builds
and its test suite passes; it proves nothing about a running deployment.

### Excluded tests and why

The exclusions are named in the workflow with their reason, and `AGENTS.md` §14 requires approval to
change them. They are not silently skipped:

* Engine: eight classes load GraphHopper from the gitignored OSM extract or are performance
  benchmarks.
* Entry: `GvrpEntryApiApplicationTests` and `DepotMapperTest` need a MySQL database.

The comment claims that gitleaks scans only the push, not the history, because history already
contains committed credentials (SEC-001). That is an accepted limitation, not a fix.

## 5. Operational behaviour that a deployment inherits

Facts a runbook must state, because they are surprising and all follow from the code:

1. **Nothing is deleted by default.** `gvrp.cleanup.dry-run=true` makes the daily job sweep log-only.
   Jobs, solutions, routes and stops accumulate indefinitely.
2. **A stuck job never recovers.** No process ages out `PENDING` or `PROCESSING`; if the engine dies
   before spooling a result, the job stays `PROCESSING` and counts against the three-job limit forever.
3. **The spool is on local disk.** A container without a volume loses pending results on restart,
   and two engine replicas do not share it.
4. **Progress is in engine memory.** `JobRegistry` holds it; after an engine restart, progress for a
   running job is gone while the job may still be alive in the solver.
5. **Both services log to stdout only.** No file appender; log retention is the platform's problem.
6. **Spring Security and Spring Web loggers are at DEBUG** in the shipped properties.
7. **Tokens live 24 hours with no revocation.** A disabled user is cut off at the next request; a
   user whose token leaked and who is still enabled cannot be cut off at all.

## 6. Startup order

Not enforced, and it matters:

1. MySQL reachable and schema applied.
2. Entry starts (`ddl-auto=validate` compares the schema and aborts on drift).
3. Engine starts (builds or loads the routing graph; this is the slow step).
4. Client served; `apiUrl` is `/api/v1`, so it must be served from the same origin as Entry or behind
   a proxy that forwards `/api`.

If the engine starts before Entry, planning requests fail until Entry is up. If Entry starts against
an empty database, it fails fast, which is the intended behaviour of `validate`.

## Sources

`GVRP_Entry_API/Dockerfile` (empty), `.github/workflows/ci.yml`, `.github/dependabot.yml`,
`GVRP_Entry_API/src/main/resources/application.properties`,
`GVRP_Entry_API/src/main/resources/application-local.properties`,
`GVRP_Entry_API/src/main/resources/database/**`, `GVRP_Entry_API/pom.xml`, `GVRP_Engine_API/pom.xml`,
`GVRP_Engine_API/src/main/resources/application.properties`, `GVRP_Engine_API/.gitignore`,
`GVRP_Entry_client_v2/package.json`, `GVRP_Entry_client_v2/proxy.conf.json`,
`GVRP_Entry_client_v2/src/environments/environment.ts`.
