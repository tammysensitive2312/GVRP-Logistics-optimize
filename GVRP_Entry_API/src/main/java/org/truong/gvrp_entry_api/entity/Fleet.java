package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fleets", indexes = {
        @Index(name = "idx_fleet_branch", columnList = "branch_id")
})
@SuperBuilder
public class Fleet extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "fleet_name", length = 100)
    private String fleetName;

    @OneToMany(mappedBy = "fleet", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Vehicle> vehicles = new ArrayList<>();

    // Business methods
    public int getTotalCapacity() {
        return vehicles.stream()
                .mapToInt(Vehicle::getCapacity)
                .sum();
    }

    public int getVehicleCount() {
        return vehicles.size();
    }

    public List<Vehicle> getAvailableVehicles() {
        return vehicles.stream()
                .filter(Vehicle::isAvailable)
                .toList();
    }
}
