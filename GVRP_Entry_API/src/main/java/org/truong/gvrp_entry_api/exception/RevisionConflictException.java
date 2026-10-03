package org.truong.gvrp_entry_api.exception;

import lombok.Getter;

/** Base solution is no longer the latest revision — HTTP 409. */
@Getter
public class RevisionConflictException extends RuntimeException {
    private final Long latestRevisionId;

    public RevisionConflictException(String message, Long latestRevisionId) {
        super(message);
        this.latestRevisionId = latestRevisionId;
    }
}
