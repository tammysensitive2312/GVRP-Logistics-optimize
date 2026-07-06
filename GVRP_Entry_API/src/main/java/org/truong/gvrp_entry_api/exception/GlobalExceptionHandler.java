package org.truong.gvrp_entry_api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        logger.warn("Validation error: {}", errors);

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "0040001",
                "Invalid input data.",
                request,
                errors);
    }

    /**
     * Handle MaxUploadSizeExceededException
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "0040005",
                "Maximum upload size exceeded.",
                request,
                null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        logger.warn("Error message: {}", ex.getMessage());

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "0030001",
                "Invalid JSON format.",
                request,
                null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        return buildResponse(
                ex.getStatus(),
                ex.getErrorCode(),
                ex.getMessage(),
                request,
                null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        logger.error("Unexpected error occurred", ex);

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "0050001",
                "Internal Server Error",
                request,
                null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request,
            Map<String, String> errors) {

        return ResponseEntity.status(status)
                .body(buildErrorResponse(
                        errorCode,
                        message,
                        request,
                        errors));
    }

    private ErrorResponse buildErrorResponse(
            String errorCode,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors) {

        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .method(request.getMethod())
                .validationErrors(validationErrors)
                .build();
    }
}