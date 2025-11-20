package org.truong.gvrp_entry_api.entity;

import org.truong.gvrp_entry_api.entity.enums.OptimizationJobStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "optimization_jobs", indexes = {
        @Index(name = "idx_job_branch", columnList = "branch_id"),
        @Index(name = "idx_job_status", columnList = "status"),
        @Index(name = "idx_job_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OptimizationJobStatus status;

    @Column(name = "external_job_id", length = 255)
    private String externalJobId;

    @Column(name = "input_data", columnDefinition = "JSON")
    private String inputData;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @OneToOne(mappedBy = "job")
    private Solution solution;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // Business methods
    public boolean canBeCancelled() {
        return status == OptimizationJobStatus.PROCESSING;
    }

    public boolean isCompleted() {
        return status == OptimizationJobStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == OptimizationJobStatus.FAILED;
    }
}
