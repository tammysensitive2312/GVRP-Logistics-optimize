package org.truong.gvrp_entry_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataInvalidException.class)
    public ResponseEntity<ErrorResponse> handleDataInvalidException(DataInvalidException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getErrors()));
    }

    @ExceptionHandler(UnprocessablePlanException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessablePlan(UnprocessablePlanException ex) {
        logger.warn("Route plan violates hard constraints: {}", ex.getMessage());

        ErrorDetail detail = ErrorDetail.builder()
                .code(ErrorCode.UNPROCESSABLE_PLAN.getCode())
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(List.of(detail)));
    }

    @ExceptionHandler(RevisionConflictException.class)
    public ResponseEntity<ErrorResponse> handleRevisionConflict(RevisionConflictException ex) {
        logger.warn("Stale revision base: {}", ex.getMessage());

        ErrorDetail detail = ErrorDetail.builder()
                .code(ErrorCode.RESOURCE_CONFLICT.getCode())
                .message(ex.getMessage() + " Latest revision id: " + ex.getLatestRevisionId())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(List.of(detail)));
    }

    @ExceptionHandler(EngineUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleEngineUnavailable(EngineUnavailableException ex) {
        logger.warn("Optimization engine unavailable: {}", ex.getMessage());

        ErrorDetail detail = ErrorDetail.builder()
                .code(ErrorCode.BACKEND_SERVER_ERROR.getCode())
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(List.of(detail)));
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

        ErrorDetail detail = ErrorDetail.builder()
                .code(ErrorCode.INTERNAL_ERROR.getCode())
                .message(ex.getMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(List.of(detail)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unexpected system error", ex);

        ErrorDetail systemError = ErrorDetail.builder()
                .code(ErrorCode.INTERNAL_ERROR.getCode())
                .message(ErrorCode.INTERNAL_ERROR.getMessage())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(List.of(systemError)));
    }
}