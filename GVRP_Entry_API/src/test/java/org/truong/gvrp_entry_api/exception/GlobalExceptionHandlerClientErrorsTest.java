package org.truong.gvrp_entry_api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.truong.gvrp_entry_api.util.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A mistake in the request is not an internal failure (LEZ-001 P1c follow-up).
 * Both cases used to reach the generic handler and answer 500.
 */
class GlobalExceptionHandlerClientErrorsTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final WebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());

    @Test
    void unreadableBodyAnswers400WithoutLeakingCauseDetails() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error",
                new IllegalArgumentException("Unrecognized field \"vehicle_features\""),
                null);

        ResponseEntity<?> response = handler.handleHttpMessageNotReadable(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorDetail detail = firstDetail(response);
        assertThat(detail.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(detail.getMessage())
                .isEqualTo("Malformed request body.")
                .doesNotContain("vehicle_features");
    }

    @Test
    void unreadableBodyDoesNotExposeInternalClassOrRejectedCustomerValue() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error",
                new IllegalArgumentException(
                        "Cannot deserialize value of type `org.truong.gvrp_entry_api.dto.request.OrderInputDTO` from String \"0901234567\""),
                null);

        ResponseEntity<?> response = handler.handleHttpMessageNotReadable(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorDetail detail = firstDetail(response);
        assertThat(detail.getMessage())
                .isEqualTo("Malformed request body.")
                .doesNotContain("org.truong")
                .doesNotContain("OrderInputDTO")
                .doesNotContain("0901234567");
    }

    @Test
    void unreadableBodyWithoutCauseTextStillAnswers400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error", new IllegalStateException(), null);

        ResponseEntity<?> response = handler.handleHttpMessageNotReadable(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(firstDetail(response).getMessage()).isEqualTo("Malformed request body.");
    }

    @Test
    void wrongMethodAnswers405() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("GET", java.util.List.of("PUT"));

        ResponseEntity<?> response = handler.handleHttpRequestMethodNotSupported(
                ex, new HttpHeaders(), HttpStatus.METHOD_NOT_ALLOWED, webRequest);

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

    private static ErrorDetail firstDetail(ResponseEntity<?> response) {
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body.getErrors()).isNotEmpty();
        return body.getErrors().get(0);
    }
}
