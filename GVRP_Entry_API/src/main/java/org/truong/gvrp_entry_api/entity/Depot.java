package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "depots", indexes = {
        @Index(name = "idx_depot_branch", columnList = "branch_id"),
        @Index(name = "idx_depot_location", columnList = "location")
})
@SuperBuilder
public class Depot extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    private String name;
    private String address;
    @Column(columnDefinition = "POINT SRID 4326", nullable = false)
    private Point location;  // JTS Point

    @OneToMany(mappedBy = "startDepot")
    @Builder.Default
    private List<Vehicle> vehiclesStartingHere = new ArrayList<>();

    @OneToMany(mappedBy = "endDepot")
    @Builder.Default
    private List<Vehicle> vehiclesEndingHere = new ArrayList<>();

    public boolean isValidLocation() {
        return location != null &&
                location.getY() >= -90 && location.getY() <= 90 &&
                location.getX() >= -180 && location.getX() <= 180;
    }

    public double getLatitude() {
        return location != null ? location.getY() : 0.0;
    }

    public double getLongitude() {
        return location != null ? location.getX() : 0.0;
    }
}
