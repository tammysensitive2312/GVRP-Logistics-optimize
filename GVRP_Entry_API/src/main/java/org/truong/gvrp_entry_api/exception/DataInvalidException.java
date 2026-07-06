package org.truong.gvrp_entry_api.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
public class DataInvalidException extends BusinessException {

    public DataInvalidException(
            String message
    ) {
        super(
                message,
                ErrorCode.VALIDATION_ERROR.getCode(),
                HttpStatus.BAD_REQUEST
        );
    }
}
