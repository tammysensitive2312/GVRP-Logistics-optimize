package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.truong.gvrp_entry_api.entity.enums.LocationType;

import java.math.BigDecimal;
import java.time.LocalTime;
import org.locationtech.jts.geom.Point;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "route_segments", indexes = {
        @Index(name = "idx_segment_route", columnList = "route_id"),
        @Index(name = "idx_segment_order", columnList = "order_id"),
        @Index(name = "idx_segment_sequence", columnList = "route_id, sequence_number")
})
@Builder
public class RouteSegment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_type", nullable = false, length = 10)
    private LocationType fromType;

    @Column(name = "from_location_id")
    private Long fromLocationId;

    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "from_location", columnDefinition = "POINT SRID 4326")
    private Point fromLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_type", nullable = false, length = 10)
    private LocationType toType;

    @Column(name = "to_location_id")
    private Long toLocationId;

    @Column(name = "to_address")
    private String toAddress;

    @Column(name = "to_location", columnDefinition = "POINT SRID 4326")
    private Point toLocation;

    @Column(precision = 10, scale = 2)
    private BigDecimal distance;

    @Column(precision = 10, scale = 2)
    private BigDecimal duration;

    @Column(name = "arrival_time")
    private LocalTime arrivalTime;

    @Column(name = "departure_time")
    private LocalTime departureTime;

    @Column(name = "service_time", precision = 10, scale = 2)
    private BigDecimal serviceTime;

    @Column(name = "load_before", precision = 10, scale = 2)
    private BigDecimal loadBefore;

    @Column(name = "load_after", precision = 10, scale = 2)
    private BigDecimal loadAfter;

    // Business methods
    public boolean isDepotToDepot() {
        return fromType == LocationType.DEPOT && toType == LocationType.DEPOT;
    }

    public boolean isDepotToOrder() {
        return fromType == LocationType.DEPOT && toType == LocationType.ORDER;
    }

    public boolean isOrderToOrder() {
        return fromType == LocationType.ORDER && toType == LocationType.ORDER;
    }

    public BigDecimal getLoadDelivered() {
        // 1. Kiểm tra null: Phải kiểm tra cả hai, nếu thiếu trả về BigDecimal.ZERO
        if (loadBefore == null || loadAfter == null) {
            return BigDecimal.ZERO;
        }

        return loadBefore.subtract(loadAfter);
    }
}