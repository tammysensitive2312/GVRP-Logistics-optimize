package org.truong.gvrp_entry_api.exception;

import org.springframework.http.HttpStatus;

public class JobLimitException extends BusinessException {
    public JobLimitException(String message) {
        super(
                message,
                ErrorCode.JOB_LIMIT_EXCEEDED.getCode(),
                HttpStatus.CONFLICT
        );
    }
}
