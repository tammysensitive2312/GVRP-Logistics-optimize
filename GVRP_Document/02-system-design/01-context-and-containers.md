# 01 — Context and containers

Two views of the same system: what it is for and who it talks to (C4 level 1), and what runs where
(C4 level 2). Both are derived from the deployment that exists in the repository — two Spring Boot
applications, one Angular SPA, one MySQL schema — not from an intended architecture.

Diagrams: [c4-context.svg](diagrams/c4-context.svg) ([source](diagrams/c4-context.puml)),
[c4-container.svg](diagrams/c4-container.svg) ([source](diagrams/c4-container.puml)).

---

## 1. System context

GVRP Logistics Optimize plans delivery routes for one branch of one company. A planner prepares
master data and orders, asks for a plan, reviews and edits the result, checks low-emission-zone
compliance, and sends the chosen route to a driver. A separate optimiser service does the actual
routing computation.

| Element | Type | Responsibility as implemented |
|---|---|---|
| Planner | Person, `PLANNER` | Everything except the driver view. Authenticated with a JWT bound to one branch. |
| Driver | Person, `DRIVER` | Reads the routes sent to them. Cannot reach planner endpoints. |
| Anonymous visitor | Person | Login screen, and solution deep links that redirect to login. |
| Branch administrator | Person, out of band | Creates branch rows and user rows and sets the Slack webhook. No endpoint and no screen exists. |
| Goong Geocoding API | External system | Address → coordinates when an order has none. |
| SMTP server | External system | Job success and failure e-mail. |
| Slack incoming webhook | External system | One webhook URL per branch row, posted on job completion. |
| OSRM public routing | External system | Client-side fallback geometry when the stored legs are incomplete. |
| Google raster tiles | External system | Base map for every Leaflet map in the client. |

Nothing else calls into the system. There is no public API product, no partner integration and no
mobile application: the driver view is the same SPA, served at `/driver`.

### What the system does not do

Stated explicitly because the surrounding documentation used to imply otherwise:

* There is no multi-company tenancy. A branch is a tenant; all branches belong to the same operator.
* There is no customer-facing surface. The `CUSTOMER` role exists and is refused by every business
  endpoint.
* There is no scheduling of recurring plans. Every plan is requested by a planner.
* There is no live vehicle tracking. The driver view reads a snapshot of a sent route; the browser's
  position is used locally to show progress.

---

## 2. Container view

| Container | Technology | Responsibility | Durable state |
|---|---|---|---|
| Client v2 | Angular 18.2.14, Angular Material 18, Leaflet 1.9.4, Transloco | All planner and driver screens; calls `/api/v1/**` on Entry | none (token in browser storage) |
| Legacy client | Plain JavaScript, no build | Older admin screens, hard-coded to `http://localhost:8080/api/v1` | none |
| Entry API | Spring Boot 3, Java 21, Spring Web, Spring Security, Spring Data JPA | REST API, JWT auth, branch scoping, job dispatch, callbacks, notifications, retention sweeps | MySQL; nothing in memory that must survive a restart |
| Engine API | Spring Boot 3, Java 21, GraphHopper, jsprit | Distance matrix, optional clustering, VRP solve, route evaluation, leg geometry | the result spool on the local file system |
| MySQL `gvrp_db` | MySQL 8, InnoDB, SRID 4326 | All business data | yes |
| Result spool | File system, `pending/` `sent/` `poison/` | Engine outbox: the result is written before the callback is posted | yes, local to the engine host |

### Interactions

| From | To | Protocol | Notes |
|---|---|---|---|
| Client v2 | Entry | HTTPS, JSON, `Authorization: Bearer` | Base path `/api/v1`; in development an Angular dev-server proxy forwards `/api` to `localhost:8080` |
| Legacy client | Entry | HTTP, JSON | Absolute URL in the source; no proxy, no auth header wiring beyond the token it stores |
| Entry | Engine | HTTP, JSON | One `RestTemplate`, 1-minute connect timeout, 3-minute read timeout. **No authentication header.** |
| Engine | Entry | HTTP, JSON, `X-API-Key` | Callbacks; Entry compares the header with its own `engine.api.key` |
| Entry | MySQL | JDBC | `ddl-auto=validate`; schema comes from SQL files, not from Hibernate |
| Engine | Result spool | File IO | Writes before posting; marks sent or poisoned afterwards |
| Entry | Goong | HTTPS | Client-side key from configuration |
| Entry | SMTP | SMTP | Plain `JavaMailSender` |
| Entry | Slack | HTTPS webhook | URL read from `branches.branch_webhook_url` |
| Client v2 | OSRM | HTTPS | Only when a stored leg has no geometry or instructions |
| Client v2 | Google tiles | HTTPS | Every map |

### Boundary decisions that carry consequences

1. **The engine never persists business data.** It has no database. A result exists only in the
   solving thread's memory until the spool writes it, so a crash between the spike and the spool
   write loses the result and the job stays `PROCESSING` forever. There is no watchdog for that state
   (`04-non-functional.md` §7, §13).
2. **Callbacks are the only completion path.** Entry does not poll the engine for a result; it polls
   only for progress. If the engine and Entry disagree about the job state, the spool and the retry
   scheduler are the only recovery mechanism.
3. **The client talks to three external hosts directly** (OSRM, Google tiles, nothing else). Every
   other integration is server-side. This means the browser's network policy, not the server's, decides
   whether maps and fallback geometry work.
4. **Entry is the only trusted component.** Branch scoping, role checks, LEZ certification and the
   job limit all live in Entry. The engine has no security layer at all: it accepts any caller and
   only *sends* the API key, on callbacks. Entry therefore trusts the engine as far as the API key
   proves identity, and the engine trusts every caller.

---

## 3. Where behaviour lives

A short map from a user-visible capability to the container that implements it. Useful when reading
`03-basic-design` because a screen is rarely served by a single container.

| Capability | Client | Entry | Engine |
|---|---|---|---|
| Login and roles | login screen, route guards | JWT filter, security rules | — |
| Master data | admin screens | CRUD endpoints, branch scoping | — |
| Order import | upload dialog | parser, geocoding, per-row errors | — |
| Planning | job dialog, progress polling | validation, snapshot, dispatch | matrix, cluster, solve |
| Solution review | map, list, timeline | read endpoints, revision chain | evaluate, legs |
| Manual edit | drag/drop editor | re-evaluate proxy, persist revision | totals and violations |
| LEZ | zone editor, compliance overlay | zone CRUD, rules compile, certification | — |
| Send to driver | send dialog | LEZ gate, leg fill, send record | legs |
| Driver view | `/driver` | driver-scoped read endpoints | — |
| Notifications | — | e-mail, Slack | — |

## Sources

`GVRP_Entry_API/pom.xml`, `GVRP_Engine_API/pom.xml`, `GVRP_Entry_client_v2/package.json`,
`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/{config,security,controller,service}/**`,
`GVRP_Engine_API/src/main/java/org/truong/gvrp_engine_api/{controller,service,job}/**`,
`GVRP_Engine_API/src/main/resources/application.properties`,
`GVRP_Entry_client_v2/src/app/**`, `GVRP_Entry_client/scripts/api/api.js`,
`GVRP_Entry_client_v2/proxy.conf.json`.
