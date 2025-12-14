package org.truong.gvrp_entry_api.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.truong.gvrp_entry_api.dto.response.OptimizationJobDTO;
import org.truong.gvrp_entry_api.entity.OptimizationJob;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OptimizationJobMapper {

    /**
     * Convert entity to DTO
     * @param job OptimizationJob entity
     * @return OptimizationJobDTO
     */
    public OptimizationJobDTO toDTO(OptimizationJob job) {
        if (job == null) {
            return null;
        }

        return OptimizationJobDTO.builder()
                .id(job.getId())
                .branchId(job.getBranch() != null ? job.getBranch().getId() : null)
                .userId(job.getCreatedBy() != null ? job.getCreatedBy().getId() : null)
                .userName(job.getCreatedBy() != null ? job.getCreatedBy().getFullName() : null)
                .status(job.getStatus())
                .externalJobId(job.getExternalJobId())
                .estimatedDurationMinutes(job.getEstimatedDurationMinutes())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .cancelledAt(job.getCancelledAt())
                .elapsedSeconds(calculateElapsedSeconds(job))
                .remainingSeconds(calculateRemainingSeconds(job))
                .canBeCancelled(job.canBeCancelled())
                .solutionId(job.getSolution() != null ? job.getSolution().getId() : null)
                .build();
    }

    /**
     * Convert list of entities to list of DTOs
     * @param jobs List of OptimizationJob entities
     * @return List of OptimizationJobDTOs
     */
    public List<OptimizationJobDTO> toDTOList(List<OptimizationJob> jobs) {
        if (jobs == null) {
            return null;
        }

        return jobs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convert DTO to entity (for create/update operations)
     * @param dto OptimizationJobDTO
     * @param solution Solution entity (if exists)
     * @return OptimizationJob entity
     */
    public OptimizationJob toEntity(OptimizationJobDTO dto, Solution solution) {
        if (dto == null) {
            return null;
        }

        return OptimizationJob.builder()
                .id(dto.getId())
                .status(dto.getStatus())
                .externalJobId(dto.getExternalJobId())
                .estimatedDurationMinutes(dto.getEstimatedDurationMinutes())
                .errorMessage(dto.getErrorMessage())
                .createdAt(dto.getCreatedAt())
                .completedAt(dto.getCompletedAt())
                .cancelledAt(dto.getCancelledAt())
                .solution(solution)
                // Note: branch and createdBy should be set separately in service layer
                .build();
    }

    /**
     * Partial update entity from DTO (only non-null fields)
     * @param dto OptimizationJobDTO with updated fields
     * @param job Existing OptimizationJob entity
     * @return Updated OptimizationJob entity
     */
    public OptimizationJob partialUpdate(OptimizationJobDTO dto, OptimizationJob job) {
        if (dto == null || job == null) {
            return job;
        }

        // Update only non-null fields
        if (dto.getStatus() != null) {
            job.setStatus(dto.getStatus());
        }
        if (dto.getExternalJobId() != null) {
            job.setExternalJobId(dto.getExternalJobId());
        }
        if (dto.getEstimatedDurationMinutes() != null) {
            job.setEstimatedDurationMinutes(dto.getEstimatedDurationMinutes());
        }
        if (dto.getErrorMessage() != null) {
            job.setErrorMessage(dto.getErrorMessage());
        }
        if (dto.getCompletedAt() != null) {
            job.setCompletedAt(dto.getCompletedAt());
        }
        if (dto.getCancelledAt() != null) {
            job.setCancelledAt(dto.getCancelledAt());
        }

        return job;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Calculate elapsed seconds from job creation to completion/now
     * @param job OptimizationJob entity
     * @return Elapsed seconds
     */
    private Long calculateElapsedSeconds(OptimizationJob job) {
        if (job == null || job.getCreatedAt() == null) {
            return 0L;
        }

        LocalDateTime endTime = job.getCompletedAt() != null
                ? job.getCompletedAt()
                : (job.getCancelledAt() != null
                ? job.getCancelledAt()
                : LocalDateTime.now());

        return ChronoUnit.SECONDS.between(job.getCreatedAt(), endTime);
    }

    /**
     * Calculate remaining seconds based on estimated duration
     * @param job OptimizationJob entity
     * @return Remaining seconds (null if not applicable)
     */
    private Integer calculateRemainingSeconds(OptimizationJob job) {
        if (job == null || job.getStatus() != OptimizationJobStatus.PROCESSING) {
            return null;
        }

        if (job.getEstimatedDurationMinutes() == null) {
            return null;
        }

        long elapsed = calculateElapsedSeconds(job);
        long estimated = job.getEstimatedDurationMinutes() * 60L;

        return Math.max(0, (int)(estimated - elapsed));
    }
}