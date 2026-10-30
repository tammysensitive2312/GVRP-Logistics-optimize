# 04 — Non-functional requirements

Non-functional behaviour **as implemented** at `c52bb17`. Nothing here is a target that was agreed
elsewhere: each row states a limit, a default or a policy that the code or its configuration enforces
today, with the file that carries it. Where a value is absent, the entry says so instead of guessing.

Status vocabulary follows `AGENTS.md` §5: `VERIFIED` (read directly from code/configuration, or asserted
by a test), `PARTIALLY_VERIFIED`, `NOT_VERIFIED` (inspection only).

---

## 1. Runtime shape

| Aspect | As implemented | Source |
|---|---|---|
| Services | Two Spring Boot applications: `GVRP_Entry_API` (API, persistence, dispatch, e-mail) and `GVRP_Engine_API` (GraphHopper + jsprit). | `GVRP_Entry_API/pom.xml`, `GVRP_Engine_API/pom.xml` |
| Ports | Entry has no `server.port`, so the Spring Boot default `8080` applies; engine binds `8081`. | `spring.application.name` only in Entry properties; `server.port=8081` in engine properties |
| Client | Angular 18.2.14 SPA, served separately; dev server proxies through `proxy.conf.json`. SSR scaffolding (`@angular/ssr`, `express`) is present and buildable. | `GVRP_Entry_client_v2/package.json`, `angular.json` |
| State | Both services are stateless with respect to sessions; Entry keeps the only durable state (MySQL) plus the engine filesystem outbox. | `SecurityConfig` (`SessionCreationPolicy.STATELESS`), `ResultSpool` |
| Database | MySQL, schema owned by SQL files under `database/`; Hibernate runs with `ddl-auto=validate`, so a schema mismatch fails startup instead of being auto-repaired. | `application.properties`, `src/main/resources/database/**` |
| Engine boundary | Entry→engine calls use a shared `RestTemplate` with a 1-minute connect timeout and a 3-minute read timeout. | `RestTemplateConfig` |

---

## 2. Concurrency and throughput limits

| Limit | Value | Enforced by | Status |
|---|---|---|---|
| Concurrent optimisation jobs per branch | 3 | `AppConstant.maxConcurrentJobs`, guard in `OptimizationJobService` (BR-060) | `VERIFIED` |
| Entry asynchronous executor | core 2, max 5, queue 100, thread prefix `optimization-` | `AsyncConfig.taskExecutor` | `VERIFIED` |
| Engine request pool | core 4, max 10, queue 100, keep-alive 60 s | engine `application.properties` (`spring.task.execution.pool.*`) | `VERIFIED` |
| Engine scheduling pool | 2 threads (`spring.task.scheduling.pool.size=2`) | engine properties | `VERIFIED` |
| Engine virtual threads | enabled (`spring.threads.virtual.enabled=true`) | engine properties | `VERIFIED` |
| Solver threads per job | 4, for every speed setting | `OptimizationConfigMapper.mapSpeed` | `VERIFIED` |
| Estimated job duration (UI only) | `min(5 + 2·orders + vehicles, 30)` minutes | `OptimizationJobService.estimateDuration` (BR-063) | `VERIFIED` |
| Simultaneous sends of one route | one winner; the loser re-reads and answers `409` | `RouteSendService` (BR-055) | `VERIFIED` |
| Duplicate completion callback | at most one solution per job | `OptimizationCallbackService` (BR-066) | `VERIFIED` |

The queue capacities are the only back-pressure mechanism. When the Entry executor queue is full, job
submission fails inside the request thread rather than being rejected explicitly; no `RejectedExecutionHandler`
or `503` mapping exists. Status: `NOT_VERIFIED` (no test, no configured rejection policy).

---

## 3. Solver budget

The planner's `speed` preference is the only budget control. `OptimizationConfigMapper` maps it to
iterations, wall-clock timeout and thread count; with no preferences the default row applies.

| Speed | `maxIterations` | `timeoutSeconds` | `numThreads` |
|---|---|---|---|
| `FAST` | 800 | 480 (labelled "3 minutes" in a comment) | 4 |
| `NORMAL` | 2000 | 900 (labelled "8 minutes") | 4 |
| `HIGH_QUALITY` | 5000 | 3600 (labelled "15 minutes") | 4 |
| default / no preferences | 2000 | 900 | 4 |

Default weights when no preferences are sent: cost 0.5, CO₂ 0.5, strict time windows,
`unassignedJobPenalty` 10000, Pareto analysis off. With preferences, the goal sets the weights
(`MINIMIZE_COST` → 1.0/0.0, `MINIMIZE_DISTANCE` → 0.5/0.5, `MINIMIZE_CO2` → `EPSILON`/1.0,
`BALANCED` → 0.5/0.5), and `allowUnassignedOrders` sets the penalty to 100 or 10000.

Status: `VERIFIED` (constants in `OptimizationConfigMapper`; the mapping is exercised by the planning tests).
The two comment labels are wrong by a factor of ~0.6 and ~0.55 respectively — 480 s is 8 minutes and
900 s is 15 minutes. The numbers, not the comments, are the contract.

Engine-side scale constants: demand is stored in solver units of 0.1 (`DEMAND_SCALE = 10`), carbon is
priced at 150 000 VND per tonne (`CARBON_PRICE_PER_TON`), `EPSILON = 0.0001` keeps the cost term non-zero,
cluster-first preprocessing starts at 1000 orders with target cluster size 150, and the Pareto sampler
uses `N = 4`, `p = 2.0` (`GVRP_Engine_API/utils/AppConstant`).

---

## 4. Client interaction limits

| Behaviour | Value | Source |
|---|---|---|
| Job progress polling | every 3000 ms while the job runs; stops on terminal state and on dialog close | `job-monitoring-dialog.component.ts` (`pollingIntervalMs ?? 3000`) |
| Degraded polling notice | after 2 consecutive poll errors the dialog flags the data as possibly stale | same component (`pollingDegraded`) |
| Progress display | clamped to 0…100; live engine percentage wins over the stored job value | same component |
| Order list paging | `page` default 0, `size` default 10 | `OrderController` (BR-026) |
| Job history | `limit` parameter on `GET /api/v1/jobs` | `OptimizationJobController` |

The client holds no offline cache and retries no writes; a failed mutation is reported as a toast and the
user repeats it.

---

## 5. Input size and file limits

| Limit | Value | Source | Status |
|---|---|---|---|
| Import file size | 10 MB per uploaded file, checked in application code before parsing | `FileValidationUtils.MAX_FILE_SIZE` | `VERIFIED` |
| Import file type | extension must end with `.csv` or `.xlsx` (lower-case comparison) | `FileValidationUtils.validate` calls, `OrderImportService` | `VERIFIED` |
| Empty or missing file | rejected with `0040004` on resource `file` | `FileValidationUtils` | `VERIFIED` |
| Zone geometry | 5000 vertices, `POLYGON`/`MULTIPOLYGON`/`FEATURE`, SRID 4326 | `GeoJsonGeometryParser` (BR-073) | `VERIFIED` |
| Zone rules | 50 per zone | `RuleValidator.MAX_RULES_PER_ZONE` (BR-074) | `VERIFIED` |
| Zone name | 200 characters | `NAME_MAX_LENGTH` (BR-072) | `VERIFIED` |
| Multipart request ceiling | `spring.servlet.multipart.max-file-size` and `max-request-size` are **commented out**, so the framework defaults apply | `application.properties` | `NOT_VERIFIED` — the effective values depend on the Spring Boot version's defaults and were not measured |

The 10 MB code check is the binding limit for order import; the servlet layer would reject a larger body
before the check runs. No other endpoint accepts a body large enough for this to matter.

---

## 6. Data policy

| Aspect | As implemented | Source |
|---|---|---|
| Schema ownership | SQL files; `init.sql`, `v2.0.sql`, `GVRP_master_initial.sql` plus seven dated migrations (2026-09-07 … 2026-10-28) | `src/main/resources/database/**` |
| Schema validation | `ddl-auto=validate` — the application refuses to start on drift | `application.properties` |
| Lazy loading | `open-in-view=false`; entities are mapped to DTOs inside the transaction | `application.properties`, service layer |
| JDBC batching | batch size 50 with ordered inserts and updates | `application.properties` |
| SQL logging | off (`show-sql=false`, Hibernate loggers commented) | `application.properties` |
| JSON contract | snake_case on both sides; dates/times serialised as ISO strings, not epoch numbers | `WebConfig.objectMapper`, engine `application.properties` |
| Pretty printing | the Entry `ObjectMapper` enables `INDENT_OUTPUT`, so responses are pretty-printed | `WebConfig.objectMapper` |

Pretty printing is production behaviour, not a dev-only setting: it inflates responses (notably the
solution detail payload) with no functional benefit. Status: `VERIFIED` (bean definition), cost
`NOT_VERIFIED` (never measured).

---

## 7. Retention and housekeeping

Two independent sweeps exist, one per service.

| Sweep | Schedule | Retention | Delete mode | Source |
|---|---|---|---|---|
| Entry job cleanup (`JobCleanupService`) | `0 30 3 * * *` daily at 03:30 | `CANCELLED` 7 d, `FAILED` 30 d, `COMPLETED` 90 d; batch 200 | hard delete, chain `route_sends → revisions → solution → job`; the trigger `trg_prevent_job_deletion_with_solution` forbids deleting a job that still owns a solution | `JobCleanupService`, `application.properties` |
| Engine spool retention (`SpoolRetentionSweeper`) | `0 0 3 * * *` daily at 03:00 | `sent/` 7 d, `poison/` 30 d; `pending/` never deleted | file delete | `SpoolRetentionSweeper`, engine properties |

`gvrp.cleanup.enabled=true` but **`gvrp.cleanup.dry-run=true`**: the job sweep currently only logs what
it *would* delete. Real deletion requires flipping that property. Status: `VERIFIED` (property default and
guard in `scheduledCleanup`). Consequence worth stating: on a default deployment no job is ever removed,
so the database grows without bound until an operator changes the flag.

`PENDING` and `PROCESSING` jobs are never swept regardless of age, so a job wedged in `PENDING` stays
forever. There is no watchdog that ages out stale `PENDING`/`PROCESSING` rows.

### Engine outbox

The engine writes every in-memory result to a filesystem spool *before* posting the callback, so a
callback failure does not lose the result:

| Parameter | Value |
|---|---|
| Root | `gvrp.result-spool.dir` (default `./.data/results`) |
| Folders | `pending/`, `sent/`, `poison/` |
| Retry schedule | initial delay 60 000 ms, then fixed delay 120 000 ms |
| Max attempts | 20 |
| Poisoned when | Entry answers 4xx, or attempts are exhausted |
| Durability | payload written to `.tmp` then atomically moved |

Status: `VERIFIED` (constructor and `CallbackRetryScheduler` values). A 4xx from Entry is treated as
permanent, which is correct for payload errors but means a temporary `4xx`-class misconfiguration
(e.g. a rotated API key answered as 401) poisons the result instead of retrying.

---

## 8. Security non-functional requirements

Full detail lives in `02-system-design/04-security-and-tenancy.md`; the non-functional envelope is:

| Aspect | As implemented | Source |
|---|---|---|
| Session model | stateless; JWT in the `Authorization: Bearer` header; CSRF disabled because no cookie session exists | `SecurityConfig` |
| Token lifetime | 86 400 000 ms (24 h), fixed; no refresh token | `spring.gvrp.jwt.expiration-ms` |
| Logout | client-side token discard only; there is no server-side revocation list. Access stops earlier only when the user is disabled or deleted (BR-008) | `AuthController`, `JwtAuthenticationFilter` |
| Password storage | BCrypt via `PasswordEncoder` bean with library defaults (work factor not configured) | `WebConfig.passwordEncoder` |
| Public surface | `/api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`, `/api/v1/solutions/callbacks/**` | `SecurityConfig` (BR-007) |
| Callback authentication | separate engine-API-key filter on both directions (Entry accepts engine callbacks, engine accepts Entry requests) | `ApiKeyAuthenticationFilter`, `entry.api-key` |
| CORS | `/api/**` from `http://localhost:*`, `http://127.0.0.1:*`, `http://192.168.*:*` with credentials, all methods, all headers, preflight cached 3600 s | `WebConfig.corsConfigurer` |
| Transport | TLS is not configured in the application; HTTPS is assumed to be terminated by the deployment front end | `NOT_VERIFIED` — no deployment descriptor in the repository |
| Secrets | read from configuration properties only; values are never documented here | `application.properties` property names |

Two observations that belong to requirements rather than design:

1. `/actuator/health` is permitted, but `spring-boot-starter-actuator` is **not** a dependency of
   `GVRP_Entry_API/pom.xml`. The rule is inert today: health is served by the engine
   (`GET /api/v1/optimization/health`), not by Entry. Status: `VERIFIED` (dependency list + security rule).
2. CORS accepts any `192.168.*.*` origin with credentials. That is wider than a development laptop and
   allows any host on a typical office LAN to call the API from a browser. Status: `VERIFIED`
   (pattern in `WebConfig`); whether it is intended is a deployment decision.

---

## 9. Logging and auditability

| Aspect | As implemented | Source |
|---|---|---|
| Root level | `INFO`; application package `INFO` | `application.properties` |
| Framework levels | `org.springframework.security=DEBUG`, `org.springframework.web=DEBUG` | `application.properties` |
| Output | console only, custom `logging.pattern.console`; no file appender, no JSON encoder | `application.properties` |
| Business audit | none. There is no audit table and no event log; the immutable records are `route_sends` (who sent what, when) and job/solution timestamps | `RouteSend` entity, `BaseEntity` auditing |
| E-mail logging | `EmailService` logs the recipient's e-mail address at INFO on send and on success | `EmailService` (`log.info("Sending success email to {}", user.getEmail())`) |

The `DEBUG` levels for Spring Security and Spring Web are shipped as defaults, so every request logs
framework-level detail in production. The e-mail address in the log line is a personal identifier and
conflicts with `AGENTS.md` §15 ("do not log personal data"). Both are recorded as input for
`docs/security/SECURITY_CHECKLIST.md`; this document only states what is implemented.

There is no request-correlation id, so a job, its engine call, its callback and its e-mail cannot be tied
together in the log except by the job id that some messages happen to include.

---

## 10. Internationalisation

| Aspect | As implemented | Source |
|---|---|---|
| Client framework | Transloco 7.5.1 (`@jsverse/transloco`) | `package.json`, `transloco-loader.ts` |
| Languages | `en` (default) and `vi` | `LanguageService`, `APP_LANGUAGES`, `DEFAULT_LANGUAGE` |
| Dictionaries | `public/i18n/en.json`, `public/i18n/vi.json`, loaded at runtime | `transloco-loader.ts` |
| Persistence of choice | through `StorageService` (SSR-safe wrapper), not direct `localStorage` | `LanguageService` |
| Server messages | English only. No `messages.properties` exists and no `MessageSource` is configured, so API error messages, validation messages and e-mail subjects are English strings in code | `resources/` inventory, `ErrorCode`, `EmailService` |
| Locale-sensitive formatting | server-side e-mail formatting uses fixed `dd/MM/yyyy HH:mm:ss` and `Locale` defaults in `NumberFormat` | `EmailService` |

Consequence: the UI is bilingual, the API is not. A Vietnamese user sees Vietnamese labels but English
validation errors. Status: `VERIFIED` (file inventory and message sources).

---

## 11. Client platform and browser support

| Aspect | As implemented | Source |
|---|---|---|
| Framework baseline | Angular 18.2.14 with Angular Material 18, TypeScript 5.4.5 | `package.json` |
| Browser targets | none declared: no `.browserslistrc` and no `browserslist` key, so the Angular CLI's default target list applies | repository inventory, `angular.json` |
| Map stack | Leaflet 1.9.4 with `@asymmetrik/ngx-leaflet` and `leaflet-polylinedecorator` | `package.json` |
| Client tests | Karma + Jasmine, Chrome launcher | `package.json`, `karma.conf.js` |
| Geolocation | driver view uses `navigator.geolocation` | `driver-view.component.ts` |
| Secure context | `navigator.geolocation` is only available in a secure context (HTTPS or `localhost`); served over plain HTTP from a LAN address the driver's position cannot be read | Web platform requirement, `ASSUMPTION:` no test covers this in the repository |

The absence of a browserslist means support is whatever the Angular CLI defaults to for version 18
(modern evergreen browsers). Older browsers are not polyfilled and not tested.

---

## 12. Configuration hygiene

Configuration keys that are declared but never read:

| Key | Declared in | Reality |
|---|---|---|
| `task.cron.expression` | Entry `application.properties` | no reader in either service; the job sweep uses `gvrp.cleanup.cron` instead |
| `schedule.task.min_gap` | Entry `application.properties` | no reader in either service |
| `spring.optimization.engine.timeout` | Entry `application.properties` (value 900) | no reader; the engine HTTP timeouts come from `RestTemplateConfig` (1 min connect / 3 min read) |

Status: `VERIFIED` (repository-wide search for each key). They are harmless but misleading: an operator
who sets `spring.optimization.engine.timeout` would expect it to bound engine calls and it does not.
`spring.servlet.multipart.*` and `upload.default.directory` are likewise commented out.

---

## 13. Open non-functional gaps

These are stated as gaps, not as requirements that were met:

1. No bound on request body size beyond the framework default (§5, `NOT_VERIFIED`).
2. No explicit rejection policy when the Entry executor queue is full (§2).
3. Job retention is configured but dry-run by default, so nothing is deleted (§7).
4. No watchdog for jobs stuck in `PENDING`/`PROCESSING` (§7).
5. No server-side logout or token revocation (§8).
6. `/actuator/health` is permitted but actuator is not installed; Entry has no health endpoint of its own (§8).
7. CORS allows any `192.168.*.*` origin with credentials (§8).
8. Framework loggers ship at `DEBUG`; e-mail addresses are logged (§9).
9. No request correlation id (§9).
10. API messages are English-only (§10).
11. No declared browser support baseline (§11).
12. Three configuration keys are inert (§12).

## Sources

`GVRP_Entry_API/src/main/resources/application.properties`,
`GVRP_Entry_API/src/main/resources/database/**`,
`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/{config,util,service,exception}/**`,
`GVRP_Entry_API/pom.xml`, `GVRP_Engine_API/src/main/resources/application.properties`,
`GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/{service,utils}/**`,
`GVRP_Entry_client_v2/package.json`, `GVRP_Entry_client_v2/src/app/core/i18n/**`,
`GVRP_Entry_client_v2/src/app/features/main/jobs/job-monitoring-dialog/**`.
