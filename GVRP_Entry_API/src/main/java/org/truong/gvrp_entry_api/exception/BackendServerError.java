package org.truong.gvrp_entry_api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BackendServerError extends BusinessException {
    public BackendServerError() {
        super("Unexpected error.", ErrorCode.INTERNAL_ERROR.getCode(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
