package org.truong.gvrp_entry_api.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_ERROR("0040001"),
    RESOURCE_NOT_FOUND("0040402"),
    RESOURCE_CONFLICT("0040901"),
    UNSUPPORTED_VALUE("0040002"),
    FILE_SIZE_EXCEEDED("0040003"),
    INTERNAL_ERROR("0050001"),
    INVALID_ORDER_TRANSITION("0040902"),
    JOB_LIMIT_EXCEEDED("0040903");

    private final String code;
}
