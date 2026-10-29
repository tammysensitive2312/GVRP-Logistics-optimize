package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.locationtech.jts.geom.MultiPolygon;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;

/**
 * A branch's use of a zone template (LEZ-R02).
 *
 * <p>LINKED rows read geometry and rules from the template's current version;
 * their name, geometry and version columns stay null. COPIED rows own their
 * geometry and {@link BranchZoneRule}s and are never overwritten by the system.
 *
 * <p>At most one LINKED row per (branch, template). MySQL has no partial unique
 * index, so {@code ZoneService} enforces it; the migration adds a generated
 * column with a unique key as a database backstop.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "branch_zones", indexes = {
        @Index(name = "idx_branch_zone_branch", columnList = "branch_id")
})
@SuperBuilder
public class BranchZone extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BranchZoneMode mode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_template_id", nullable = false)
    private ZoneTemplate sourceTemplate;

    /** COPIED: the template version copied. LINKED: null. */
    @Column(name = "source_template_version")
    private Integer sourceTemplateVersion;

    /** COPIED: the latest template version the planner acknowledged. */
    @Column(name = "acknowledged_template_version")
    private Integer acknowledgedTemplateVersion;

    /** COPIED only. */
    @Column(length = 200)
    private String name;

    /** COPIED only; x = longitude, y = latitude. */
    @Column(columnDefinition = "MULTIPOLYGON SRID 4326")
    private MultiPolygon geometry;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = Boolean.TRUE;
}
