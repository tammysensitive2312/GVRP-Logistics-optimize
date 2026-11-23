package org.truong.gvrp_entry_api.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== SHARED RESPONSE HELPERS ====================

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatus status, ErrorCode code, String message) {
        return errorResponse(status, ErrorDetails.of(code, null, null, message));
    }

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatus status, ErrorDetail detail) {
        return ResponseEntity.status(status).body(new ErrorResponse(List.of(detail)));
    }

    private ResponseEntity<Object> mvcErrorResponse(HttpStatusCode status, ErrorDetail detail) {
        return mvcErrorResponse(status, List.of(detail));
    }

    private ResponseEntity<Object> mvcErrorResponse(HttpStatusCode status, List<ErrorDetail> details) {
        return ResponseEntity.status(status).body(new ErrorResponse(details));
    }

    // ==================== DOMAIN EXCEPTIONS ====================

    @ExceptionHandler(DataInvalidException.class)
    public ResponseEntity<ErrorResponse> handleDataInvalidException(DataInvalidException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getErrors()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        logger.warn("Forbidden: {}", ex.getMessage());
        return errorResponse(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return errorResponse(HttpStatus.NOT_FOUND, ErrorDetails.of(
                ErrorCode.RESOURCE_NOT_FOUND, ex.getResource(), ex.getField(),
                ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
    }

    /** 409 — the request collides with an existing resource or state (SEC-007, E-2, E-4). */
    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ErrorResponse> handleResourceConflict(ResourceConflictException ex) {
        return errorResponse(HttpStatus.CONFLICT, ErrorDetails.of(
                ErrorCode.RESOURCE_CONFLICT, ex.getResource(), ex.getField(), ex.getMessage()));
    }

    @ExceptionHandler(UnprocessablePlanException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessablePlan(UnprocessablePlanException ex) {
        logger.warn("Route plan violates hard constraints: {}", ex.getMessage());
        return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.UNPROCESSABLE_PLAN, ex.getMessage());
    }

    /** 422 — completion callback outside the job's branch (SEC-013). */
    @ExceptionHandler(CallbackRejectedException.class)
    public ResponseEntity<ErrorResponse> handleCallbackRejected(CallbackRejectedException ex) {
        logger.warn("Callback rejected: {}", ex.getMessage());
        return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_ERROR, ex.getMessage());
    }

    @ExceptionHandler(RevisionConflictException.class)
    public ResponseEntity<ErrorResponse> handleRevisionConflict(RevisionConflictException ex) {
        logger.warn("Stale revision base: {}", ex.getMessage());
        return errorResponse(HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                ex.getMessage() + " Latest revision id: " + ex.getLatestRevisionId());
    }

    @ExceptionHandler(EngineUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleEngineUnavailable(EngineUnavailableException ex) {
        logger.warn("Optimization engine unavailable: {}", ex.getMessage());
        return errorResponse(HttpStatus.BAD_GATEWAY, ErrorCode.BACKEND_SERVER_ERROR, ex.getMessage());
    }

    /**
     * 503 — báo cho engine biết đây là lỗi tạm thời, cứ gửi lại.
     *
     * <p>Phải là 5xx: engine chỉ retry với 5xx/timeout, còn 4xx bị coi là payload sai
     * và chuyển thẳng sang poison/. Log ở mức WARN không kèm stack trace vì đây là
     * tình huống lành tính, không phải sự cố.
     */
    @ExceptionHandler(JobNotReadyException.class)
    public ResponseEntity<ErrorResponse> handleJobNotReady(JobNotReadyException ex) {
        logger.warn("⏳ Callback tới sớm: {}", ex.getMessage());
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR, ex.getMessage());
    }

    // ==================== SECURITY & AUTHENTICATION (E-1, E-7) ====================

    /**
     * 401 — handles both failed login ({@link BadCredentialsException},
     * {@link AccountStatusException}) and filter-chain authentication rejections
     * forwarded by {@code JwtAuthenticationEntryPoint} via {@code HandlerExceptionResolver}.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        logger.warn("Authentication failed ({})", ex.getClass().getSimpleName());
        if (ex instanceof BadCredentialsException || ex instanceof AccountStatusException) {
            return errorResponse(HttpStatus.UNAUTHORIZED, ErrorCode.INCORRECT_LOGIN_INFORMATION,
                    ErrorCode.INCORRECT_LOGIN_INFORMATION.getMessage());
        }
        return errorResponse(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.getMessage());
    }

    /**
     * 403 — handles both {@code @PreAuthorize} method-security denials (E-7)
     * and filter-chain denials forwarded by {@code RoleAccessDeniedHandler}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        logger.warn("Access denied ({})", ex.getClass().getSimpleName());
        return errorResponse(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage());
    }

    // ==================== VALIDATION & SPRING MVC ERRORS (E-6) ====================

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        logger.warn("Constraint validation failed: {} violation(s)", ex.getConstraintViolations().size());
        List<ErrorDetail> details = ex.getConstraintViolations().stream()
                .map(violation -> ErrorDetails.unsupportedValue(
                        extractLeafPropertyName(violation), violation.getMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(details));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("Unsupported value (IllegalArgumentException)");
        return errorResponse(HttpStatus.BAD_REQUEST, ErrorCode.UNSUPPORTED_VALUE,
                ErrorCode.UNSUPPORTED_VALUE.getMessage());
    }

    /**
     * 400 — unreadable body (malformed JSON, unknown field). Returns a fixed neutral
     * message and logs only the cause class name so internal class/package names and
     * rejected customer values are never exposed (SEC-019).
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        Throwable cause = ex.getMostSpecificCause();
        logger.warn("Malformed request body ({})", cause.getClass().getSimpleName());

        return mvcErrorResponse(HttpStatus.BAD_REQUEST,
                ErrorDetails.validation("Malformed request body."));
    }

    /**
     * 400 — bean-validation failure of a request body, one detail per error in
     * the order Spring reports them. The log line carries the count and object name only.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        BindingResult result = ex.getBindingResult();
        logger.warn("Request validation failed: {} error(s) on {}",
                result.getErrorCount(), result.getObjectName());

        List<ErrorDetail> details = result.getAllErrors().stream()
                .map(error -> ErrorDetails.validation(
                        error instanceof FieldError fieldError ? fieldError.getField() : null,
                        error.getDefaultMessage()))
                .toList();

        return mvcErrorResponse(HttpStatus.BAD_REQUEST, details);
    }

    /**
     * 400 — wrong parameter/path variable type (e.g., string where a Long ID is expected).
     * Never logs {@code ex.getValue()} to avoid leaking untrusted input.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String field = (ex instanceof MethodArgumentTypeMismatchException mismatch)
                ? mismatch.getName()
                : ex.getPropertyName();
        logger.warn("Invalid value type for parameter '{}' ({})", field, ex.getClass().getSimpleName());

        return mvcErrorResponse(HttpStatus.BAD_REQUEST, ErrorDetails.of(
                ErrorCode.INVALID_VALUE_TYPE, null, field, ErrorCode.INVALID_VALUE_TYPE.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        logger.warn("Missing required parameter '{}'", ex.getParameterName());
        return mvcErrorResponse(HttpStatus.BAD_REQUEST, ErrorDetails.of(
                ErrorCode.EMPTY_FIELD_ERROR, null, ex.getParameterName(),
                "Missing required parameter: " + ex.getParameterName()));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ErrorDetail> details = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String paramName = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> details.add(
                    ErrorDetails.unsupportedValue(paramName, error.getDefaultMessage())));
        });
        if (details.isEmpty()) {
            details.add(ErrorDetails.of(ErrorCode.UNSUPPORTED_VALUE));
        }
        logger.warn("Method parameter validation failed: {} error(s)", details.size());
        return mvcErrorResponse(HttpStatus.BAD_REQUEST, details);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        logger.warn("Resource not found: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return mvcErrorResponse(HttpStatus.NOT_FOUND, ErrorDetails.of(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 405 — a supported URL called with the wrong method. */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        logger.warn("Unsupported request method: {}", ex.getMessage());
        return mvcErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, ErrorDetails.of(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        logger.warn("Unsupported media type: {}", ex.getContentType());
        return mvcErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorDetails.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        logger.warn("Media type not acceptable");
        // Omit body when client rejects JSON so HttpMessageConverter does not rethrow 406.
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        logger.warn("MVC error ({}): status={}", ex.getClass().getSimpleName(), statusCode.value());
        ErrorCode code = statusCode.is4xxClientError()
                ? ErrorCode.VALIDATION_ERROR
                : ErrorCode.INTERNAL_ERROR;
        return mvcErrorResponse(statusCode, ErrorDetails.of(code));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unexpected system error", ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private static String extractLeafPropertyName(ConstraintViolation<?> violation) {
        String leaf = null;
        for (Path.Node node : violation.getPropertyPath()) {
            leaf = node.getName();
        }
        return leaf;
    }
}
