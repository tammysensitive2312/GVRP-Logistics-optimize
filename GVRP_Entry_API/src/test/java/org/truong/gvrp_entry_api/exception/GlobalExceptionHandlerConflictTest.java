package org.truong.gvrp_entry_api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.truong.gvrp_entry_api.util.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-LOW-001 LOW-T5: a resource conflict (for example a duplicate license
 * plate, SEC-007) answers 409 RESOURCE_CONFLICT with the exception message,
 * not the generic 500.
 */
class GlobalExceptionHandlerConflictTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceConflictAnswers409WithItsMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleResourceConflict(
                new ResourceConflictException("License plate is not available."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrors()).hasSize(1);
        ErrorDetail detail = response.getBody().getErrors().get(0);
        assertThat(detail.getCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT.getCode());
        assertThat(detail.getMessage()).isEqualTo("License plate is not available.");
        assertThat(detail.getResource()).isNull();
    }
}
