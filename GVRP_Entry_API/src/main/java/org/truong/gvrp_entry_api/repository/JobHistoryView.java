package org.truong.gvrp_entry_api.repository;

import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;

import java.time.LocalDateTime;

/** Scalar job-history fields; deliberately excludes the input payload. */
public interface JobHistoryView {
    Long getId();
    Long getBranchId();
    Long getUserId();
    String getUserName();
    OptimizationJobStatus getStatus();
    String getExternalJobId();
    Integer getEstimatedDurationMinutes();
    String getErrorMessage();
    LocalDateTime getCreatedAt();
    LocalDateTime getCompletedAt();
    LocalDateTime getCancelledAt();
    Long getSolutionId();
}
