package org.truong.gvrp_entry_api.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResourceConflictException extends RuntimeException {
    private String resourceName;
    public ResourceConflictException(String message, String resourceName) {
        super(message);
        this.resourceName = resourceName;
    }
}
