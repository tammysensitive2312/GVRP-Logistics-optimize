package org.truong.gvrp_entry_api.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class DataInvalidException extends RuntimeException {

    private final List<ErrorDetail> errors;

    public DataInvalidException(List<ErrorDetail> errors) {
        super();
        this.errors = errors;
    }
}
