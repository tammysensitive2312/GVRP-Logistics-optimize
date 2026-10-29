package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.locationtech.jts.geom.MultiPolygon;
import org.truong.gvrp_entry_api.entity.enums.BoundaryConfidence;

/**
 * System-level zone template (LEZ-R01). It has no branch: every branch may read
 * it, none may write it. Written only by migrations or seed data in P1.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "zone_templates")
@SuperBuilder
public class ZoneTemplate extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 20)
    private String jurisdiction;

    /** x = longitude, y = latitude, like {@code Order.location}. */
    @Column(columnDefinition = "MULTIPOLYGON SRID 4326", nullable = false)
    private MultiPolygon geometry;

    /** Raised by one on every change of geometry or rules. */
    @Column(nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "boundary_confidence", nullable = false, length = 20)
    private BoundaryConfidence boundaryConfidence;

    @Column(name = "boundary_source", columnDefinition = "TEXT")
    private String boundarySource;

    @Column(name = "legal_basis", columnDefinition = "TEXT")
    private String legalBasis;
}
