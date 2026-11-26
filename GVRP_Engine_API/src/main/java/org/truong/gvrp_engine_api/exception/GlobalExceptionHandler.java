package org.truong.gvrp_engine_api.exception;

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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== SHARED RESPONSE HELPERS ====================

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatusCode status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(ErrorDetails.of(code, null, null, message)));
    }

    private ResponseEntity<Object> mvcErrorResponse(HttpStatusCode status, ErrorDetail detail) {
        return mvcErrorResponse(status, List.of(detail));
    }

    private ResponseEntity<Object> mvcErrorResponse(HttpStatusCode status, List<ErrorDetail> details) {
        return ResponseEntity.status(status).body(ErrorResponse.of(details));
    }

    // ==================== DOMAIN & APPLICATION EXCEPTIONS ====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : ErrorCode.VALIDATION_ERROR.getMessage();
        logger.warn("Validation rejected: {}", message);
        return errorResponse(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        ErrorCode code = mapStatusToErrorCode(status);
        String message;
        if (status.is5xxServerError()) {
            message = (status.value() == HttpStatus.BAD_GATEWAY.value()
                    && ex.getReason() != null && !ex.getReason().isBlank())
                    ? ex.getReason()
                    : code.getMessage();
            logger.warn("Engine status error {}: {}", status.value(), ex.getReason());
        } else {
            message = (ex.getReason() != null && !ex.getReason().isBlank())
                    ? ex.getReason()
                    : code.getMessage();
            logger.warn("Request rejected ({}): {}", status.value(), message);
        }
        return errorResponse(status, code, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        logger.warn("Constraint validation failed: {} violation(s)", ex.getConstraintViolations().size());
        List<ErrorDetail> details = ex.getConstraintViolations().stream()
                .map(violation -> ErrorDetails.unsupportedValue(
                        extractLeafPropertyName(violation), violation.getMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(details));
    }

    // ==================== SPRING MVC ERRORS ====================

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
        logger.error("Unexpected error occurred", ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private static ErrorCode mapStatusToErrorCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.VALIDATION_ERROR;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 409 -> ErrorCode.RESOURCE_CONFLICT;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 502 -> ErrorCode.BACKEND_SERVER_ERROR;
            default -> status.is4xxClientError() ? ErrorCode.VALIDATION_ERROR : ErrorCode.INTERNAL_ERROR;
        };
    }

    private static String extractLeafPropertyName(ConstraintViolation<?> violation) {
        String leaf = null;
        for (Path.Node node : violation.getPropertyPath()) {
            leaf = node.getName();
        }
        return leaf;
    }
}
