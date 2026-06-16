package org.truong.gvrp_entry_api.util;

import lombok.Builder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;

import java.time.LocalDateTime;

@Getter
public class JobCompletionEvent extends ApplicationEvent {

    private final Long jobId;
    private final Long branchId;
    private final String branchName;
    private final Long userId;

    private final Long solutionId;
    private final SolutionStatus solutionStatus;
    private final double totalDistance;
    private final double totalCost;
    private final double totalCO2;
    private final int totalVehiclesUsed;
    private final int servedOrdersCount;
    private final int unservedOrdersCount;

    private final LocalDateTime completedAt;
    private final String errorMessage;

    @Builder
    public JobCompletionEvent(Object source, Long jobId, Long branchId,
                              String branchName, Long userId, Long solutionId,
                              SolutionStatus solutionStatus, double totalDistance,
                              double totalCost, double totalCO2, int totalVehiclesUsed,
                              int servedOrdersCount, int unservedOrdersCount,
                              LocalDateTime completedAt, String errorMessage) {
        super(source);
        this.jobId = jobId;
        this.branchId = branchId;
        this.branchName = branchName;
        this.userId = userId;
        this.solutionId = solutionId;
        this.solutionStatus = solutionStatus;
        this.totalDistance = totalDistance;
        this.totalCost = totalCost;
        this.totalCO2 = totalCO2;
        this.totalVehiclesUsed = totalVehiclesUsed;
        this.servedOrdersCount = servedOrdersCount;
        this.unservedOrdersCount = unservedOrdersCount;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }
}
