package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.truong.gvrp_entry_api.entity.enums.VehicleStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vehicles", indexes = {
        @Index(name = "idx_vehicle_fleet", columnList = "fleet_id"),
        @Index(name = "idx_vehicle_status", columnList = "status"),
        @Index(name = "idx_vehicle_license", columnList = "vehicle_license_plate", unique = true)
})
@SuperBuilder
public class Vehicle extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fleet_id", nullable = false)
    private Fleet fleet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_depot_id")
    private Depot startDepot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "end_depot_id")
    private Depot endDepot;

    @Column(name = "vehicle_license_plate", nullable = false, unique = true, length = 20)
    private String vehicleLicensePlate;

    @Column(name = "vehicle_feature")
    private String vehicleFeature;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "fixed_cost", precision = 10, scale = 2)
    private BigDecimal fixedCost;

    @Column(name = "cost_per_km", precision = 10, scale = 2)
    private BigDecimal costPerKm;

    @Column(name = "cost_per_hour", precision = 10, scale = 2)
    private BigDecimal costPerHour;

    @Column(name = "max_distance", precision = 10, scale = 2)
    private BigDecimal maxDistance;

    @Column(name = "max_duration", precision = 10, scale = 2)
    private BigDecimal maxDuration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    @OneToMany(mappedBy = "vehicle")
    @Builder.Default
    private List<Route> routes = new ArrayList<>();

    // Business methods
    public boolean isAvailable() {
        return status == VehicleStatus.AVAILABLE;
    }

    public boolean canFulfill(Double demand) {
        return demand != null && capacity >= demand;
    }
}
