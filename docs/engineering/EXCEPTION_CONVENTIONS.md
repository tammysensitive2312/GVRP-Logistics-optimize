# GVRP Entry API — Exception & Error-Code Conventions

Date: 2026-11-23

This note records the exception, HTTP status, and error-detail conventions for `GVRP_Entry_API` (`Q-6`).

## 1. Exception Package & Reuse

* Place shared domain and HTTP exceptions under `org.truong.gvrp_entry_api.exception`.
* Reuse the existing domain exceptions instead of creating feature-specific duplicates (for example, `LezNotFoundException` and `LezConflictException` were removed in favor of `ResourceNotFoundException` and `ResourceConflictException`).
* Build `ErrorDetail` objects via `ErrorDetails` factory helpers (`of`, `validation`, `validationWithField`, `validationWithResource`, `unsupportedValue`) rather than repeating inline `ErrorDetail.builder()` blocks.

## 2. HTTP Status & Exception Mapping

`GlobalExceptionHandler` (`@RestControllerAdvice`, extending `ResponseEntityExceptionHandler`) maps exceptions to `{ "errors": [ ErrorDetail... ] }`:

| HTTP Status | Exception / Trigger | `ErrorCode` | Notes |
|---|---|---|---|
| `400 Bad Request` | `DataInvalidException` | Varies per `ErrorDetail` (typically `VALIDATION_ERROR` `0040001`, `UNSUPPORTED_VALUE` `0040002`, `FILE_SIZE_EXCEEDED` `0040003`, `EMPTY_FIELD_ERROR` `0040004`) | Preserves the caller's `List<ErrorDetail>`. |
| `400 Bad Request` | `MethodArgumentNotValidException` | `VALIDATION_ERROR` (`0040001`) | Request-body Bean Validation (`@Valid`), one detail per error with `field`. |
| `400 Bad Request` | `HttpMessageNotReadableException` | `VALIDATION_ERROR` (`0040001`) | Fixed `"Malformed request body."` message; never leaks Jackson/rejected input (`SEC-019`). |
| `400 Bad Request` | `ConstraintViolationException`, `HandlerMethodValidationException`, `IllegalArgumentException` | `UNSUPPORTED_VALUE` (`0040002`) | Out-of-range or unsupported values (e.g., pagination/limit `@Min`/`@Max` bounds). |
| `400 Bad Request` | `TypeMismatchException` / `MethodArgumentTypeMismatchException` | `INVALID_VALUE_TYPE` (`0040005`) | Wrong parameter/path type (e.g., string where a `Long` is expected); never logs raw value. |
| `400 Bad Request` | `MissingServletRequestParameterException` | `EMPTY_FIELD_ERROR` (`0040004`) | Missing required query/form parameter. |
| `401 Unauthorized` | `AuthenticationException` (login & `JwtAuthenticationEntryPoint`) | `INCORRECT_LOGIN_INFORMATION` (`0040101`) or `UNAUTHORIZED` (`0040102`) | Filter-chain 401s forward through `HandlerExceptionResolver` to `GlobalExceptionHandler`. |
| `403 Forbidden` | `ForbiddenException`, `AccessDeniedException` (including `@PreAuthorize` & `RoleAccessDeniedHandler`) | `FORBIDDEN` (`0040301`) | Role checks and missing branch/principal context. |
| `404 Not Found` | `ResourceNotFoundException`, `NoResourceFoundException` | `RESOURCE_NOT_FOUND` (`0040402`) | Carries `resource` and optional `field` (e.g., `startDepotId`, `endDepotId`). Response message is neutral (`ErrorCode.RESOURCE_NOT_FOUND.getMessage()`). |
| `405 Method Not Allowed` | `HttpRequestMethodNotSupportedException` | `METHOD_NOT_ALLOWED` (`0040501`) | — |
| `406 Not Acceptable` | `HttpMediaTypeNotAcceptableException` | _(empty body)_ | Body omitted so `HttpMessageConverter` does not rethrow `406`. |
| `409 Conflict` | `ResourceConflictException`, `RevisionConflictException` | `RESOURCE_CONFLICT` (`0040901`) | Preserves `ex.getMessage()`, optional `resource`, and optional `field`. |
| `415 Unsupported Media Type` | `HttpMediaTypeNotSupportedException` | `UNSUPPORTED_MEDIA_TYPE` (`0041501`) | Fixed MVC override in `GlobalExceptionHandler`. |
| `422 Unprocessable Entity` | `UnprocessablePlanException`, `CallbackRejectedException` | `UNPROCESSABLE_PLAN` (`0042201`), `VALIDATION_ERROR` (`0040001`) | Constraint violations / cross-branch engine callback rejection. |
| `500 Internal Server Error` | Unhandled `Exception` (and non-4xx `handleExceptionInternal`) | `INTERNAL_ERROR` (`0050001`) | Never leaks exception class/stack trace to HTTP response. |
| `502 Bad Gateway` | `EngineUnavailableException` | `BACKEND_SERVER_ERROR` (`0050002`) | Upstream routing engine communication failure. |
| `503 Service Unavailable` | `JobNotReadyException` | `INTERNAL_ERROR` (`0050001`) | Signals the engine to retry early completion callbacks. |

## 3. Validation Error Code Selection

Use distinct codes consistently for `400 Bad Request`:

* `UNSUPPORTED_VALUE` (`0040002`): syntactically valid type, but value is outside supported bounds or enum/choice rules (for example, `page < 0`, `size > max`, `limit < 1`).
* `INVALID_VALUE_TYPE` (`0040005`): type mismatch when binding query/path parameters (`TypeMismatchException`).
* `EMPTY_FIELD_ERROR` (`0040004`): required field or query parameter is missing/empty.
* `VALIDATION_ERROR` (`0040001`): request-body `@Valid` errors, unreadable JSON, and domain structural rules.

## 4. Pagination & History Limit Enforcement

* Enforce identical bounds at both controller parameters (`@Validated` + `@Min`/`@Max`) and service layer (`PaginationUtils.pageRequest` / `PaginationUtils.limitRequest`).
* Both layers emit `UNSUPPORTED_VALUE` (`0040002`) with `field` set to `"page"`, `"size"`, or `"limit"`.

## 5. Tenant-Isolation Neutrality (`AGENTS.md` §15)

* Cross-branch ID lookups and missing IDs must throw the same `ResourceNotFoundException(resource)` with identical HTTP `404` status, neutral message (`"Resource not found."`), and identical `resource`/`field` metadata.
* `ResourceConflictException` (`409`) messages must never reveal whether a conflicting value exists in another branch (e.g., license-plate uniqueness in `VehicleService`).

## Sources

* `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/exception/GlobalExceptionHandler.java`
* `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/exception/ErrorDetails.java`
* `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/exception/ResourceNotFoundException.java`
* `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/exception/ResourceConflictException.java`
* `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/util/ErrorCode.java`
* `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/util/PaginationUtils.java`
* `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/service/VehicleService.java`
