package org.truong.gvrp_entry_api.entity.enums;

/**
 * Outcome of the last low-emission-zone compliance check of a route (LEZ-001).
 *
 * <p>{@code STALE} is deliberately absent: it is derived on read by comparing
 * the stored ruleset hash with the current one (design §16.2-6).
 */
public enum LezStatus {
    UNCHECKED,
    COMPLIANT,
    VIOLATION
}
