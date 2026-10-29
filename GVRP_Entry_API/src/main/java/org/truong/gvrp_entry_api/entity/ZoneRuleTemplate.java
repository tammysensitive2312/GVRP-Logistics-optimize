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

/** A rule of one template version (design §2.1). No branch. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "zone_rule_templates", indexes = {
        @Index(name = "idx_zone_rule_template_version", columnList = "zone_template_id, template_version")
})
@SuperBuilder
public class ZoneRuleTemplate extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_template_id", nullable = false)
    private ZoneTemplate zoneTemplate;

    /** The template version this rule belongs to. */
    @Column(name = "template_version", nullable = false)
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
