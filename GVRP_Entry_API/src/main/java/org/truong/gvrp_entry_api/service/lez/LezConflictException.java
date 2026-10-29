package org.truong.gvrp_entry_api.service.lez;

/**
 * The request conflicts with the zone's state — HTTP 409. Examples: a second
 * LINKED zone for one template, editing a LINKED zone, deleting a zone that a
 * permit references.
 */
public class LezConflictException extends RuntimeException {
    public LezConflictException(String message) {
        super(message);
    }
}
