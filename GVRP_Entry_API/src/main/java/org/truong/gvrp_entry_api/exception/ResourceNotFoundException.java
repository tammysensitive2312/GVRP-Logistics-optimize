package org.truong.gvrp_entry_api.exception;

import lombok.Getter;

/**
 * A requested or referenced resource does not exist in the caller's branch —
 * HTTP 404 RESOURCE_NOT_FOUND (E-3, E-5).
 *
 * <p>Another branch's ID must answer identically to a missing ID.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {
    private final String resource;
    private final String field;

    public ResourceNotFoundException(String resource) {
        this(resource, null);
    }

    public ResourceNotFoundException(String resource, String field) {
        super(resource + " not found");
        this.resource = resource;
        this.field = field;
    }
}
