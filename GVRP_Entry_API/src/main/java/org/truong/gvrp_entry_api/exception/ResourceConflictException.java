package org.truong.gvrp_entry_api.exception;

/**
 * The request collides with an existing resource — HTTP 409 RESOURCE_CONFLICT.
 *
 * <p>The message is returned to the caller as is, so it must not reveal
 * anything about another branch (for example which branch holds a value).
 */
public class ResourceConflictException extends RuntimeException {
    public ResourceConflictException(String message) {
        super(message);
    }
}
