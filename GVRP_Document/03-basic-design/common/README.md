# Common conventions — request and response contract

Everything in this folder describes **one domain** of the running system: its screens, its endpoints,
its errors, its data. This page holds the parts that are identical everywhere, so that D01…D09 do not
repeat them nine times.

Where a domain deviates, the domain README says so and links back here.

---

## 1. Transport

| Aspect | Value |
|---|---|
| Base path | `/api/v1` (fixed in `server.servlet.context-path`-free controllers, i.e. written into each `@RequestMapping`) |
| Media type | `application/json`, UTF-8 |
| Auth header | `Authorization: Bearer <jwt>` on every non-public request |
| API key header | `X-API-Key` on `/api/v1/solutions/callbacks/**` only |
| CORS | `/api/**` from `localhost`, `127.0.0.1` and any `192.168.*.*` origin, credentials allowed |
| CSRF | disabled |
| API versioning | path only; there is no version negotiation and no second version |

The client is configured with `apiUrl = '/api/v1'`, so in a deployment the SPA must be served from the
same origin as Entry or behind a proxy that forwards `/api`. `proxy.conf.json` does this for the Angular
dev server only.

---

## 2. Naming

| Layer | Convention |
|---|---|
| JSON field names | `snake_case` — `spring.jackson.property-naming-strategy=SNAKE_CASE`, applied globally to requests and responses |
| Java fields | `camelCase` |
| Database columns | `snake_case` (see `02-system-design/05-data-architecture.md`) |
| Enumerations | upper-case tokens (`PROCESSING`, `COMPLIANT`, `ON_ROUTE`) |
| Ids | `bigint`, serialised as JSON numbers |

### The one place the convention leaks

Bean-validation failures report the **Java** field name, not the JSON name. A request body that uses
`full_name` produces:

```json
{ "errors": [ { "code": "0040001", "message": "must not be blank", "field": "fullName" } ] }
```

Observed at runtime on `POST /api/v1/auth/register`, 2026-10-30. A client that maps `field` back to a form
control by stripping the JSON path cannot do it mechanically: it needs a camelCase→snake_case conversion,
or a fixed mapping table. Every other error producer uses the JSON name or no field at all.

---

## 3. Success responses

| Shape | Used for |
|---|---|
| `201 Created` with a DTO | master-data creates: depots, fleets, vehicles, vehicle types, zones, vehicle permits |
| `200 OK` with a DTO | every other read and write, including order and job creates and solution revisions |
| `200 OK` with `PageResponse<T>` | paginated lists (orders, vehicles) |
| `200 OK` with an empty body | `POST /api/v1/auth/register` |
| `204 No Content` | `PATCH /api/v1/orders/status`, `PUT /api/v1/jobs`, `DELETE /api/v1/zones/{id}`, `DELETE /vehicles/{id}/permits/{permitId}`, and `GET /api/v1/jobs/{id}/progress` when the engine has no progress to report |
| `202 Accepted` | never returned by Entry; it is the engine's answer to dispatch |

There is no `Location` header on create and no `ETag` anywhere. The split between `200` and `201` follows no
stated rule: it is whatever the individual controller method returns, so a client cannot generalise from the
verb.

### `PageResponse<T>`

| Field | Type | Meaning |
|---|---|---|
| `content` | array of `T` | the page |
| `page_no` | int | zero-based page index |
| `page_size` | int | requested size |
| `total_elements` | long | rows matching the filter |
| `total_pages` | int | `ceil(total_elements / page_size)` |
| `last` | boolean | Spring Data's `Page.isLast()` |

Request parameters are `page` (default `0`) and `size` (default `10`). There is no maximum size and no
validation on either: `size=100000` is accepted and executed. Filters differ per endpoint and are listed
in the domain README.

---

## 4. Error envelope

The standard body is a wrapper with an **array**, because one request can produce several errors:

```json
{
  "errors": [
    {
      "code": "0040402",
      "message": "Resource not found.",
      "resource": "orders",
      "field": "address"
    }
  ]
}
```

| Field | Presence | Content |
|---|---|---|
| `code` | always | one of the `ErrorCode` tokens (table below) |
| `message` | always | English sentence; for validation, the annotation's default message |
| `resource` | only for `0040402` | the logical resource name: `orders`, `solutions`, `branches`, `pareto_candidates`, … |
| `field` | only for validation errors | see §2 for the naming inconsistency |

`ErrorDetail` is annotated `@JsonInclude(NON_NULL)`, so an absent key means "not applicable" and never
`null`. Empty arrays are not produced: an error response always carries at least one detail.

### Codes

| Code | Message | Meaning |
|---|---|---|
| `0040001` | Invalid data validation. | validation failure, malformed body |
| `0040002` | Unsupported value. | an enum or option outside the accepted set |
| `0040003` | File size exceed limit. | import file too large |
| `0040004` | Empty required field. | required value missing |
| `0040101` | Incorrect login information. | **declared but never returned** — see §5 |
| `0040301` | Access forbidden. | role or ownership rejection |
| `0040402` | Resource not found. | no such row *within the branch* |
| `0040501` | Method not allowed. | supported URL, wrong verb |
| `0040901` | Resource conflict. | duplicate, or a state that forbids the operation |
| `0040902` | Invalid order status transition. | order status machine violation |
| `0040903` | Branch reached job limit. | the three-job rule |
| `0042201` | Route plan violates hard constraints. | plan rejected before it is sent |
| `0050001` | Internal server error. | unexpected failure; **also used for `503`** |
| `0050002` | Backend server error. | engine unavailable (`502`) |
| `0050003` | Database access error. | declared, not thrown by any handler |

---

## 5. The status code is decided by the exception class, not by the code

This is the single most misleading aspect of the API, and it is the reason the domain documents list the
HTTP status and the code **separately**. `GlobalExceptionHandler` maps a class to a status; the `code`
inside the body is chosen by whatever built the detail. The two do not have to agree.

| Exception thrown | HTTP status | `code` in the body |
|---|---|---|
| `DataInvalidException` | **400** | whatever the throw site put in it — usually `0040001`, but `0040402` for "not found" and `0040901`/`0040903` for conflicts |
| `ForbiddenException` | **403** | `0040301` |
| `LezNotFoundException` | **404** | `0040402` |
| `LezConflictException` | **409** | `0040901` |
| `RevisionConflictException` | **409** | `0040901`, message suffixed `Latest revision id: <id>` |
| `UnprocessablePlanException` | **422** | `0042201` |
| `EngineUnavailableException` | **502** | `0050002` |
| `JobNotReadyException` | **503** | `0050001` — the `INTERNAL_ERROR` code on a `503` |
| `HttpMessageNotReadableException` | 400 | `0040001`, message `Malformed request body: <parser cause>` |
| `MethodArgumentNotValidException` | 400 | `0040001`, one detail per field |
| `HttpRequestMethodNotSupportedException` | 405 | `0040501` |
| any other `Exception` | 500 | `0050001` |

So a client must **not** infer the HTTP status from the code, and must not infer "not found" from a `404`.
A missing row is usually `400` + `0040402`; only the LEZ services throw the exception that produces a real
`404`. This was verified by test, not only by reading: `ParetoControllerTest#getFrontier_otherBranch_returns400NotFoundEnvelope`
asserts the `400` and documents the deviation in a comment.

### The login hole this creates

`AuthController.login` calls `authenticationManager.authenticate`. A wrong password raises
`BadCredentialsException`, an `AuthenticationException`, which no handler matches — so it reaches
`@ExceptionHandler(Exception.class)` and becomes **`500` + `0050001`**, not `401` + `0040101`. The
`0040101` code exists in `ErrorCode` and is never used. Status `PARTIALLY_VERIFIED`: this is a static
derivation from the handler table; no test and no runtime probe covers a failed login, and the branch
lookup failure in front of it (`400` + `0040402` on an unknown branch name) was observed only because a
nonexistent branch fails earlier.

---

## 6. Three error bodies that are not the envelope

| Producer | Status | Body |
|---|---|---|
| `JwtAuthenticationEntryPoint` | 401 | `{"path":"/api/v1/orders/1","error":"Unauthorized","message":"Full authentication is required to access this resource","status":401}` |
| `ApiKeyAuthenticationFilter` | 401 | `{"error": "Unauthorized", "message": "Missing API key"}` (a hand-written string; note the spaces) |
| `RoleAccessDeniedHandler` | 403 | the standard envelope with `0040301` |

The first two are emitted by servlet filters, before any controller advice can run. Any client-side error
parser must accept three shapes plus the envelope.

Authentication is evaluated **before** routing: an unauthenticated request to a path that does not exist
returns `401`, not `404`. Observed on `GET /api/v1/nope`.

---

## 7. Request validation

Two mechanisms, used inconsistently:

| Mechanism | Where | Failure |
|---|---|---|
| Bean validation (`@Valid`, `@Validated(Groups)`) | request bodies | 400, one error detail per violated constraint, unordered |
| Hand-written checks in the service | business rules that depend on the database | 400 with a detail built by the service |

Validation groups exist (`OrderInputDTO.OnCreate`, `OnUpdate`), so the same DTO accepts different
required fields on create and update. There is no validation on query parameters except parsing: an
unparseable `date` is converted to `400` + `0040001` with `field: "date"`, but an out-of-range `size` or a
negative `page` is passed to Spring Data unchanged.

---

## 8. Time, numbers, and other scalar formats

| Type | Serialised as | Note |
|---|---|---|
| Dates (`LocalDate`) | `YYYY-MM-DD` | request parameter `date` uses the same form |
| Timestamps (`LocalDateTime`) | ISO-8601 without offset | no explicit format is configured, so Spring Boot's default applies; `PARTIALLY_VERIFIED` — not observed on the wire |
| Decimals (demand, cost, emission) | JSON number | `BigDecimal` fields; scale comes from the column, no rounding is applied on output |
| Enumerations | the enum token as a string | `@Enumerated(EnumType.STRING)` end to end |
| Booleans | `true` / `false` | |
| `null` | omitted for `ErrorDetail` only; DTO fields serialise as `null` | no global `NON_NULL` on the DTOs |

Coordinates are **not** GeoJSON in the API. Requests and responses use flat `latitude` / `longitude` pairs;
the `POINT` columns are converted in the entity layer. The one exception is zone geometry, which travels as
a list of coordinate pairs in a JSON object.

---

## 9. Concurrency

| Mechanism | Where |
|---|---|
| Revision chain (`parent_solution_id`) + `base_revision_id` on edit | solution editing; a stale base raises `RevisionConflictException` → `409` |
| Single `CURRENT` `route_sends` row per route | route sending |
| Nothing | everywhere else — no `@Version`, no `If-Match`, no row locks |

Two planners editing the same order, vehicle or depot produce last-write-wins. The documents call this out
per domain where it can lose work.

---

## 10. Runtime-observed behaviour

Measured on 2026-10-30 against the local development database, Entry started on port 18080 from
`target/classes` (schema `validate` passed, 61 mappings, 8.7 s startup):

| Probe | Result |
|---|---|
| `POST /auth/login` with an unknown branch | `400`, `0040402`, `resource: branches` |
| `GET /orders/1` with no token | `401`, entry-point body |
| `POST /solutions/callbacks/complete` with no `X-API-Key` | `401`, `{"error":"Unauthorized","message":"Missing API key"}` |
| `GET /api/v1/nope` with no token | `401` (not `404`) |
| `GET /actuator/health` | `500`, `0050001` — the path is `permitAll` but actuator is not a dependency |
| malformed JSON body | `400`, `0040001`, `Malformed request body: …` |
| bean validation with four violations | `400`, four details, camelCase `field` names |
| `POST /auth/register` with valid input | `200`, `User registered successfully`, nothing written to the database |

The last row is a stub, not a feature: `AuthController.register` has no persistence.

## Sources

`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/exception/**`
(`GlobalExceptionHandler`, `ErrorDetail`, `ErrorResponse`, `ErrorDetails`, `DataInvalidException`,
`ForbiddenException`, `UnprocessablePlanException`, `RevisionConflictException`,
`EngineUnavailableException`, `JobNotReadyException`),
`.../util/ErrorCode.java`, `.../security/{JwtAuthenticationEntryPoint,ApiKeyAuthenticationFilter,RoleAccessDeniedHandler}.java`,
`.../dto/response/PageResponse.java`, `.../dto/request/{LoginRequest,RegisterRequest}.java`,
`.../controller/{OrderController,AuthController}.java`,
`GVRP_Entry_API/src/main/resources/application.properties` (`spring.jackson.property-naming-strategy`),
`GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/controller/ParetoControllerTest.java`,
`GVRP_Entry_client_v2/src/environments/environment.ts`, `GVRP_Entry_client_v2/proxy.conf.json`,
and the runtime probes recorded in §10.
