    package org.truong.gvrp_entry_api.entity;

    import jakarta.persistence.*;
    import lombok.*;
    import org.hibernate.annotations.CreationTimestamp;

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
        @GeneratedValue(strategy = GenerationType.IDENTITY)
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

        @CreationTimestamp
        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;

        // Relationships
        @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderBy("sequenceNumber ASC")
        @Builder.Default
        private List<RouteSegment> segments = new ArrayList<>();

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

        public RouteSegment getFirstSegment() {
            return segments.isEmpty() ? null : segments.get(0);
        }

        public RouteSegment getLastSegment() {
            return segments.isEmpty() ? null : segments.get(segments.size() - 1);
        }
    }
