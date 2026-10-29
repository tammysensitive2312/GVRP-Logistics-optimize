package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.SendRouteRequest;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.RevisionConflictException;
import org.truong.gvrp_entry_api.exception.UnprocessablePlanException;
import org.truong.gvrp_entry_api.service.lez.RouteComplianceService;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shape of the fix for review-p1b F1: a refused LEZ send must commit its verdict
 * instead of waiting on a row lock held by the same thread.
 *
 * <p>Unit tests cannot show the lock behaviour itself (no MySQL), so these checks
 * pin the three properties the fix depends on: the refusal is exempt from rollback,
 * no compliance method opens a second transaction, and the exceptions thrown after
 * the guard are not subclasses of the refusal (Spring applies {@code noRollbackFor}
 * to subclasses too). The real lock is verified manually by the user.
 */
class RouteSendTransactionTest {

    @Test
    void sendRouteTreatsTheLezRefusalAsCommitWorthy() throws Exception {
        Transactional transactional = RouteSendService.class
                .getMethod("sendRoute", Long.class, SendRouteRequest.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional, "sendRoute must stay transactional");
        assertTrue(Arrays.asList(transactional.noRollbackFor()).contains(UnprocessablePlanException.class),
                "the 422 must commit the verdict and the filled legs; noRollbackFor="
                        + Arrays.toString(transactional.noRollbackFor()));
    }

    @Test
    void noComplianceMethodOpensASecondTransaction() {
        for (Method method : RouteComplianceService.class.getMethods()) {
            Transactional transactional = method.getAnnotation(Transactional.class);
            if (transactional != null) {
                assertNotEquals(Propagation.REQUIRES_NEW, transactional.propagation(),
                        method.getName() + " must not open a second transaction on the locked route");
            }
        }
    }

    @Test
    void failuresAfterTheGuardStillRollBack() {
        // noRollbackFor also covers subclasses, so these two must stay siblings of
        // UnprocessablePlanException, never its children.
        assertFalse(UnprocessablePlanException.class.isAssignableFrom(RevisionConflictException.class));
        assertFalse(UnprocessablePlanException.class.isAssignableFrom(DataInvalidException.class));
    }
}
