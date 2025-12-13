package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vehicle_types")
@SuperBuilder
public class VehicleType extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Column(name = "vehicle_features", columnDefinition = "JSON")
    private String vehicleFeatures;

    @Column(name = "description")
    private String description;

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
}
