package org.truong.gvrp_entry_api.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
public class DataInvalidException extends RuntimeException {
    public DataInvalidException(String message) {
        super(message);
    }
}
