package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.truong.gvrp_entry_api.entity.enums.LezStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "routes", indexes = {
        @Index(name = "idx_route_solution", columnList = "solution_id"),
        @Index(name = "idx_route_vehicle", columnList = "vehicle_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "route_seq")
    @SequenceGenerator(
            name = "route_seq",
            sequenceName = "route_sequence",
            allocationSize = 50
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solution_id", nullable = false)
    private Solution solution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "route_order", nullable = false)
    private Integer routeOrder;

    @Column(precision = 10, scale = 2)
    private BigDecimal distance;

    @Column(name = "co2_emission", precision = 10, scale = 2)
    private BigDecimal co2Emission;

    @Column(name = "service_time", precision = 10, scale = 2)
    private BigDecimal serviceTime;

    @Column(name = "order_count")
    private Integer orderCount;

    @Column(name = "load_utilization", precision = 5, scale = 2)
    private BigDecimal loadUtilization;

    // ==================== LEZ COMPLIANCE (LEZ-001) ====================

    /** Outcome of the last compliance check; null on rows created before P1b. */
    @Enumerated(EnumType.STRING)
    @Column(name = "lez_status", length = 20)
    private LezStatus lezStatus;

    @Column(name = "lez_checked_at")
    private LocalDateTime lezCheckedAt;

    /** SHA-256 of the compiled restrictions the check used; {@code STALE} is derived from it. */
    @Column(name = "lez_ruleset_hash", columnDefinition = "CHAR(64)")
    private String lezRulesetHash;

    /** JSON array of violations; null when compliant or unchecked. */
    @Column(name = "lez_violations", columnDefinition = "JSON")
    private String lezViolations;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Relationships
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    @Builder.Default
    @BatchSize(size = 50)
    private List<RouteStop> segments = new ArrayList<>();

    // Business methods
    public LocalTime getEstimatedStartTime() {
        if (segments.isEmpty()) return null;
        return segments.get(0).getDepartureTime();
    }

    public LocalTime getEstimatedEndTime() {
        if (segments.isEmpty()) return null;
        return segments.get(segments.size() - 1).getArrivalTime();
    }

    public boolean isOverloaded() {
        return loadUtilization != null &&
                loadUtilization.compareTo(BigDecimal.valueOf(100.0)) > 0;
    }

}
