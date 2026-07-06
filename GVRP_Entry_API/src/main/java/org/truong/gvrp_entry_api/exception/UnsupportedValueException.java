package org.truong.gvrp_entry_api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class UnsupportedValueException extends BusinessException {
    private final String fieldName;

    public UnsupportedValueException(String message, String fieldName) {
        super(
                message,
                ErrorCode.UNSUPPORTED_VALUE.getCode(),
                HttpStatus.BAD_REQUEST);
        this.fieldName = fieldName;
    }
}
