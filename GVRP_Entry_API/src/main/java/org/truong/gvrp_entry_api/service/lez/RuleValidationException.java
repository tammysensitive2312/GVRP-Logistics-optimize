package org.truong.gvrp_entry_api.service.lez;

/**
 * A selector, schedule, rule or permit is not well-formed. Services translate
 * it into an HTTP 400; the evaluator itself stays free of web types.
 */
public class RuleValidationException extends RuntimeException {
    public RuleValidationException(String message) {
        super(message);
    }
}
