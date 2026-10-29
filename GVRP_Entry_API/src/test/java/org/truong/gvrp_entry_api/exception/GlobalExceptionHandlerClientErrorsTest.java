package org.truong.gvrp_entry_api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.truong.gvrp_entry_api.util.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A mistake in the request is not an internal failure (LEZ-001 P1c follow-up).
 * Both cases used to reach the generic handler and answer 500.
 */
class GlobalExceptionHandlerClientErrorsTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unreadableBodyAnswers400WithTheDeepestCause() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error",
                new IllegalArgumentException("Unrecognized field \"vehicle_features\""),
                null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorDetail detail = firstDetail(response);
        assertThat(detail.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(detail.getMessage())
                .startsWith("Malformed request body")
                .contains("Unrecognized field \"vehicle_features\"");
    }

    @Test
    void unreadableBodyWithoutCauseTextStillAnswers400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error", new IllegalStateException(), null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(firstDetail(response).getMessage()).isEqualTo("Malformed request body.");
    }

    @Test
    void wrongMethodAnswers405() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("GET", java.util.List.of("PUT"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        ErrorDetail detail = firstDetail(response);
        assertThat(detail.getCode()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.getCode());
        assertThat(detail.getMessage()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    @Test
    void unexpectedFailuresStayServerErrors() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGenericException(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(firstDetail(response).getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
    }

    private static ErrorDetail firstDetail(ResponseEntity<ErrorResponse> response) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrors()).isNotEmpty();
        return response.getBody().getErrors().get(0);
    }
}
