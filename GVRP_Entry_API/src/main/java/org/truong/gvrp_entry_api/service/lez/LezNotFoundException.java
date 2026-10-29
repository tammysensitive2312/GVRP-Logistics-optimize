package org.truong.gvrp_entry_api.service.lez;

import lombok.Getter;

/**
 * A zone, template, permit or vehicle does not exist in the caller's branch —
 * HTTP 404. Another branch's ID answers exactly like a missing one.
 */
@Getter
public class LezNotFoundException extends RuntimeException {
    private final String resource;

    public LezNotFoundException(String resource) {
        super(resource + " not found");
        this.resource = resource;
    }
}
