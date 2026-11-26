package org.truong.gvrp_engine_api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetail(String code, String message, String resource, String field) {
}
