package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "route_stops", indexes = {
        @Index(name = "idx_stop_route", columnList = "route_id"),
        @Index(name = "idx_stop_order", columnList = "order_id"),
        @Index(name = "idx_stop_sequence", columnList = "route_id, sequence_number")
})
@Builder
public class RouteStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private LocationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "location_id")
    private String locationId;

    @Column(name = "location_name")
    private String locationName;

    @Column(name = "location", columnDefinition = "POINT SRID 4326")
    private Point location;

    @Column(name = "arrival_time")
    private LocalTime arrivalTime;

    @Column(name = "departure_time")
    private LocalTime departureTime;

    @Column(name = "service_time", precision = 10, scale = 2)
    private BigDecimal serviceTime;  // minutes

    @Column(name = "wait_time", precision = 10, scale = 2)
    private BigDecimal waitTime;  // minutes

    // ==================== LOAD INFO ====================

    @Column(name = "demand", precision = 10, scale = 2)
    private BigDecimal demand;

    @Column(name = "load_after", precision = 10, scale = 2)
    private BigDecimal loadAfter;

    @Column(name = "distance_to_next", precision = 10, scale = 2)
    private BigDecimal distanceToNext;

    @Column(name = "time_to_next", precision = 10, scale = 2)
    private BigDecimal timeToNext;

    public boolean isDepot() {
        return type == LocationType.DEPOT;
    }

    public boolean isCustomer() {
        return type == LocationType.ORDER;
    }

    public boolean isFirstStop() {
        return sequenceNumber == 0;
    }

    public BigDecimal getLoadBefore() {
        if (loadAfter == null || demand == null) {
            return loadAfter;
        }
        return loadAfter.add(demand);
    }
}
