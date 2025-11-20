package org.truong.gvrp_entry_api.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnsupportedValueException extends IllegalArgumentException {
    public String fieldName;

    public UnsupportedValueException(String message, String fieldName) {
        super(message);
        this.fieldName = fieldName;
    }
}
