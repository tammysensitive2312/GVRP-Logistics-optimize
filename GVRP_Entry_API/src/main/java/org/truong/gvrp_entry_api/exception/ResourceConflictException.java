package org.truong.gvrp_entry_api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ResourceConflictException extends BusinessException {
    private final String resourceName;
    public ResourceConflictException(String message, String resourceName) {
        super(
                message,
                ErrorCode.RESOURCE_CONFLICT.getCode(),
                HttpStatus.CONFLICT);
        this.resourceName = resourceName;
    }
}
