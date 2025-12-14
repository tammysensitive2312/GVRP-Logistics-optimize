package org.truong.gvrp_entry_api.dto.response;

import lombok.*;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationJobDTO {
    private Long id;
    private Long branchId;
    private Long userId;
    private String userName;
    private OptimizationJobStatus status;
    private String externalJobId;
    private Integer estimatedDurationMinutes;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;

    // Computed fields
    private Long elapsedSeconds;
    private Integer remainingSeconds;
    private Boolean canBeCancelled;
    // Solution reference (if completed)
    private Long solutionId;
}