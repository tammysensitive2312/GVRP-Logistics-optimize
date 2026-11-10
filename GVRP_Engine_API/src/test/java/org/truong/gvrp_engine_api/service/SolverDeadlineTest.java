package org.truong.gvrp_engine_api.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** SEC-ENGINE-001 EN-T8: stop on cancel or once the job-wide runtime cap has passed. */
class SolverDeadlineTest {

    private static final Instant START = Instant.parse("2026-11-10T08:00:00Z");

    private static SolverDeadline at(Instant now, AtomicBoolean cancel) {
        return new SolverDeadline(cancel::get, START, Duration.ofSeconds(7200), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void continuesBeforeTheCap() {
        SolverDeadline d = at(START.plusSeconds(3769), new AtomicBoolean());
        assertFalse(d.shouldStop());
        assertFalse(d.deadlineHit());
    }

    @Test
    void capIsInclusiveThenStops() {
        assertFalse(at(START.plusSeconds(7200), new AtomicBoolean()).shouldStop());
        SolverDeadline past = at(START.plusSeconds(7201), new AtomicBoolean());
        assertTrue(past.shouldStop());
        assertTrue(past.deadlineHit());
        assertEquals(START.plusSeconds(7200), past.deadline());
    }

    @Test
    void cancelStopsWithoutMarkingTheDeadline() {
        AtomicBoolean cancel = new AtomicBoolean(true);
        SolverDeadline d = at(START.plusSeconds(10), cancel);
        assertTrue(d.shouldStop());
        assertFalse(d.deadlineHit());
    }
}
