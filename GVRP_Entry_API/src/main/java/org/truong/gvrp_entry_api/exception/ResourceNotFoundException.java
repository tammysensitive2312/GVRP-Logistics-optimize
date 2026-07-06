package org.truong.gvrp_entry_api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ResourceNotFoundException extends BusinessException {
    private final String resourceName;
    public ResourceNotFoundException(String message, String resourceName) {
        super(
                message,
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                HttpStatus.NOT_FOUND
                );
        this.resourceName = resourceName;
    }
}
