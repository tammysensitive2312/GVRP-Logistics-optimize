package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.truong.gvrp_entry_api.service.lez.RuleSchedule;

import java.time.LocalDate;

/**
 * Exemption of one vehicle for one branch zone (LEZ-R05). The vehicle and the
 * zone must belong to the permit's branch; checked by {@code VehiclePermitService}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vehicle_permits", indexes = {
        @Index(name = "idx_vehicle_permit_branch", columnList = "branch_id"),
        @Index(name = "idx_vehicle_permit_vehicle", columnList = "vehicle_id"),
        @Index(name = "idx_vehicle_permit_zone", columnList = "branch_zone_id")
})
@SuperBuilder
public class VehiclePermit extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_zone_id", nullable = false)
    private BranchZone branchZone;

    @Column(name = "permit_no", length = 100)
    private String permitNo;

    /** Null = the whole day; otherwise see {@code PermitView}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schedule", columnDefinition = "JSON")
    private RuleSchedule schedule;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
