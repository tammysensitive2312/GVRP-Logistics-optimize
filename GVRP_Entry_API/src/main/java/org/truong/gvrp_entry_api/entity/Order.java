package org.truong.gvrp_entry_api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.locationtech.jts.geom.Point;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_branch", columnList = "branch_id"),
        @Index(name = "idx_order_status", columnList = "status"),
        @Index(name = "idx_order_code", columnList = "order_code, branch_id", unique = true),
        @Index(name = "idx_order_location", columnList = "location")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Order extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "order_code", nullable = false, length = 50)
    private String orderCode;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(nullable = false)
    private String address;

    @Column(columnDefinition = "POINT SRID 4326", nullable = false)
    private Point location;

    @JsonIgnore
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal demand;

    @Column(name = "service_time")
    private Integer serviceTime;

    @Column(name = "time_window_start")
    private LocalTime timeWindowStart;

    @Column(name = "time_window_end")
    private LocalTime timeWindowEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.SCHEDULED;

    @Column
    private Integer priority;

    @Column(name = "delivery_notes", columnDefinition = "TEXT")
    private String deliveryNotes;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    // Relationships
    @OneToMany(mappedBy = "order")
    @Builder.Default
    private List<RouteSegment> routeSegments = new ArrayList<>();

    // Business methods
    public boolean hasTimeWindow() {
        return timeWindowStart != null && timeWindowEnd != null;
    }

    public boolean isWithinTimeWindow(LocalTime time) {
        if (!hasTimeWindow()) return true;
        return !time.isBefore(timeWindowStart) && !time.isAfter(timeWindowEnd);
    }

    public boolean isUrgent() {
        return priority != null && priority == 1;
    }

    public boolean isHighPriority() {
        return priority != null && priority <= 3;
    }

    public boolean canBeScheduled() {
        return status == OrderStatus.SCHEDULED;
    }

    @JsonProperty("latitude")
    @Transient
    public double getLatitude() {
        return location != null ? location.getY() : 0.0;
    }

    @JsonProperty("longitude")
    @Transient
    public double getLongitude() {
        return location != null ? location.getX() : 0.0;
    }
}
