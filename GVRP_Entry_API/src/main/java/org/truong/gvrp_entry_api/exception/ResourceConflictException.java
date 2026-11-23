package org.truong.gvrp_entry_api.exception;

import lombok.Getter;

/**
 * The request collides with an existing resource or state — HTTP 409 RESOURCE_CONFLICT.
 *
 * <p>The message is returned to the caller as is, so it must not reveal
 * anything about another branch (for example which branch holds a value).
 */
@Getter
public class ResourceConflictException extends RuntimeException {
    private final String resource;
    private final String field;

    public ResourceConflictException(String message) {
        this(message, null, null);
    }

    public ResourceConflictException(String message, String resource, String field) {
        super(message);
        this.resource = resource;
        this.field = field;
    }
}
