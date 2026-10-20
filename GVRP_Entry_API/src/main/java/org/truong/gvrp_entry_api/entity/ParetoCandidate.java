package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.truong.gvrp_entry_api.entity.enums.ParetoFrontierStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One point of a Pareto job's candidate frontier (PARETO-001, table {@code pareto_candidates}).
 *
 * <p>Branch-scoped: {@code branch} is the job's branch and every read goes through
 * {@code findByIdAndBranchId} / {@code findByJobIdAndBranchId...}.
 *
 * <p>{@code planJson} stores the received {@code {"routes": [...], "unassigned_orders": [...]}}
 * payload for {@code FRONTIER} candidates only; it is what {@code ParetoService.apply}
 * materialises into a {@code PARETO_ALTERNATIVE} revision.
 */
@Entity
@Table(name = "pareto_candidates", indexes = {
        @Index(name = "idx_pareto_branch", columnList = "branch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParetoCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private OptimizationJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "candidate_index", nullable = false)
    private Integer candidateIndex;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(name = "cost_weight", nullable = false, precision = 10, scale = 6)
    private BigDecimal costWeight;

    @Column(name = "co2_weight", nullable = false, precision = 10, scale = 6)
    private BigDecimal co2Weight;

    @Column(name = "total_cost", precision = 18, scale = 2)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "frontier_status", nullable = false, length = 20)
    private ParetoFrontierStatus frontierStatus;

    @Column(name = "engine_selected", nullable = false)
    private Boolean engineSelected;

    @Column(nullable = false)
    private Boolean clustered;

    @Column(name = "cluster_count")
    private Integer clusterCount;

    @Column(name = "plan_json", columnDefinition = "JSON")
    private String planJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
