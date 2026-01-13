package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "solutions", indexes = {
        @Index(name = "idx_solution_branch", columnList = "branch_id"),
        @Index(name = "idx_solution_status", columnList = "status"),
        @Index(name = "idx_solution_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Solution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @OneToOne
    @JoinColumn(name = "job_id", nullable = false)
    private OptimizationJob job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SolutionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SolutionType type;

    @Column(name = "total_cost", precision = 10, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "total_distance", precision = 10, scale = 2)
    private BigDecimal totalDistance;

    @Column(name = "total_co2", precision = 10, scale = 2)
    private BigDecimal totalCO2;

    @Column(name = "total_time", precision = 10, scale = 2)
    private BigDecimal totalTime;

    @Column(name = "total_vehicles_used")
    private Integer totalVehiclesUsed;

    @Column(name = "served_orders")
    private Integer servedOrders;

    @Column(name = "unserved_orders")
    private Integer unservedOrders;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "solution", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @BatchSize(size = 20)
    private List<Route> routes = new ArrayList<>();

    @OneToMany(mappedBy = "solution", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UnassignedOrder> unassignedOrders = new ArrayList<>();

    // Business methods
    public int getNumberOfRoutes() {
        return routes.size();
    }

    public boolean isFeasible() {
        return status == SolutionStatus.SUCCESS;
    }

    public boolean isEngineGenerated() {
        return type == SolutionType.ENGINE_GENERATED;
    }

    public boolean isFileImported() {
        return type == SolutionType.FILE_IMPORTED;
    }
}
