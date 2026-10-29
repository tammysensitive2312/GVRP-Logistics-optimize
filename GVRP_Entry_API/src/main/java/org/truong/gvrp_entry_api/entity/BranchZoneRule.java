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
import org.truong.gvrp_entry_api.service.lez.RuleSelector;

import java.time.LocalDate;

/** A rule of a COPIED branch zone; same columns as {@link ZoneRuleTemplate} plus the branch. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "branch_zone_rules", indexes = {
        @Index(name = "idx_branch_zone_rule_branch", columnList = "branch_id"),
        @Index(name = "idx_branch_zone_rule_zone", columnList = "branch_zone_id")
})
@SuperBuilder
public class BranchZoneRule extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_zone_id", nullable = false)
    private BranchZone branchZone;

    /** Template version the rule was copied from; null for rules the branch wrote. */
    @Column(name = "template_version")
    private Integer templateVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selector", columnDefinition = "JSON", nullable = false)
    private RuleSelector selector;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schedule", columnDefinition = "JSON", nullable = false)
    private RuleSchedule schedule;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null = open-ended. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "legal_basis", columnDefinition = "TEXT")
    private String legalBasis;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
