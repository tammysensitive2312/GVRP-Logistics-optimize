package org.truong.gvrp_entry_api.service.lez;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehiclePermit;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.entity.ZoneRuleTemplate;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.entity.enums.EnergyType;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.WEDNESDAY;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.point;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.square;

/**
 * {@link RestrictionCompiler} with repository substitutes (LEZ-001 P1b,
 * RC-T1…RC-T3). DB-free: the entities are fixtures, the repositories are mocks.
 */
@ExtendWith(MockitoExtension.class)
class RestrictionCompilerTest {

    private static final long BRANCH = 1L;
    private static final long TEMPLATE_ID = 10L;

    @Mock
    private BranchZoneRepository zoneRepository;
    @Mock
    private BranchZoneRuleRepository zoneRuleRepository;
    @Mock
    private ZoneRuleTemplateRepository ruleTemplateRepository;
    @Mock
    private VehiclePermitRepository permitRepository;

    @InjectMocks
    private RestrictionCompiler compiler;

    private ZoneTemplate template;
    private Depot depotInside;
    private Depot depotOutside;
    private Vehicle dieselInside;
    private Vehicle electricOutside;

    @BeforeEach
    void setUp() {
        template = ZoneTemplate.builder()
                .id(TEMPLATE_ID)
                .code("TEST")
                .name("Test zone")
                .jurisdiction("XX")
                .geometry(square(106.005, 9.995, 106.015, 10.005))
                .version(2)
                .build();

        depotInside = Depot.builder().id(1L).location(point(10.0, 106.010)).build();
        depotOutside = Depot.builder().id(2L).location(point(10.0, 106.200)).build();

        dieselInside = Vehicle.builder()
                .id(7L).vehicleType(type(EnergyType.DIESEL)).startDepot(depotInside).endDepot(depotInside)
                .build();
        electricOutside = Vehicle.builder()
                .id(8L).vehicleType(type(EnergyType.ELECTRIC)).startDepot(depotOutside).endDepot(depotOutside)
                .build();

        stubRules(2);
    }

    // ---------- fixtures ----------

    private static VehicleType type(EnergyType energy) {
        return VehicleType.builder()
                .id(1L).typeName("T").capacity(1500)
                .legalClass(LegalClass.TRUCK).energyType(energy)
                .grossWeightKg(4000).emissionStandard(5)
                .build();
    }

    private void stubRules(int version) {
        lenient().when(ruleTemplateRepository
                        .findByZoneTemplateIdAndTemplateVersionOrderByIdAsc(TEMPLATE_ID, version))
                .thenReturn(List.of(ZoneRuleTemplate.builder()
                        .id(1L)
                        .zoneTemplate(template)
                        .templateVersion(version)
                        .selector(new RuleSelector(List.of(LegalClass.TRUCK), List.of(EnergyType.DIESEL),
                                null, null, null, null))
                        .schedule(new RuleSchedule(RuleSchedule.Type.ALWAYS_FORBIDDEN, null, null))
                        .effectiveFrom(LocalDate.of(2026, 1, 1))
                        .build()));
    }

    private BranchZone linkedZone(long id, boolean enabled) {
        return BranchZone.builder()
                .id(id).branch(org.truong.gvrp_entry_api.entity.Branch.builder().id(BRANCH).build())
                .mode(BranchZoneMode.LINKED)
                .sourceTemplate(template)
                .enabled(enabled)
                .build();
    }

    // ---------- RC-T1 ----------

    @Test
    void rcT1_onlyTheEnabledZoneIsCompiled() {
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of(
                linkedZone(1L, true),
                linkedZone(2L, false)));

        CompiledRestrictions restrictions = compiler.compile(BRANCH, WEDNESDAY,
                List.of(dieselInside), List.of());

        assertThat(restrictions.getZones()).hasSize(1);
        assertThat(restrictions.getZones().get(0).getKey()).isEqualTo("Z1");
        assertThat(restrictions.getZones().get(0).getBranchZoneId()).isEqualTo(1L);
        assertThat(restrictions.getZones().get(0).getMode()).isEqualTo(BranchZoneMode.LINKED);
        assertThat(restrictions.getZones().get(0).getTemplateVersion()).isEqualTo(2);
        assertThat(restrictions.restriction(7L, "Z2")).isNull();
    }

    @Test
    void rcT1_noEnabledZone_givesTheEmptyCompilationOfDesign167() {
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of(
                linkedZone(2L, false)));

        CompiledRestrictions restrictions = compiler.compile(BRANCH, WEDNESDAY,
                List.of(dieselInside), List.of());

        assertThat(restrictions.hasZones()).isFalse();
        assertThat(restrictions.getHash()).isNotBlank();
        assertThat(restrictions.getHash()).isEqualTo(
                compiler.compile(BRANCH, WEDNESDAY, List.of(), List.of()).getHash(),
                "the hash of an empty branch depends on the date only");
    }

    // ---------- RC-T2 ----------

    @Test
    void rcT2_depotInAllDayZone_makesOnlyTheBannedVehicleUnusable() {
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of(linkedZone(1L, true)));

        CompiledRestrictions restrictions = compiler.compile(BRANCH, WEDNESDAY,
                List.of(dieselInside, electricOutside), List.of());

        assertThat(restrictions.getUnusableVehicles()).hasSize(1);
        CompiledRestrictions.UnusableVehicle unusable = restrictions.getUnusableVehicles().get(0);
        assertThat(unusable.getVehicleId()).isEqualTo(7L);
        assertThat(unusable.getReason()).isEqualTo(CompiledRestrictions.REASON_DEPOT_IN_ZONE_ALL_DAY);
        assertThat(unusable.getZoneKey()).isEqualTo("Z1");
        assertThat(restrictions.unusableVehicleIds()).containsOnly(7L);

        assertThat(restrictions.restriction(7L, "Z1").isAllDay()).isTrue();
        assertThat(restrictions.restriction(8L, "Z1").getKind())
                .isEqualTo(ZoneVerdict.Kind.ALLOWED.name());
    }

    // ---------- RC-T3 ----------

    private String hash(List<Vehicle> vehicles, boolean enabled) {
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH))
                .thenReturn(List.of(linkedZone(1L, enabled)));
        return compiler.compile(BRANCH, WEDNESDAY, vehicles, List.of()).getHash();
    }

    @Test
    void rcT3_equalInput_givesTheSameHash() {
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of(linkedZone(1L, true)));

        String first = compiler.compile(BRANCH, WEDNESDAY, List.of(dieselInside), List.of()).getHash();
        String second = compiler.compile(BRANCH, WEDNESDAY, List.of(dieselInside), List.of()).getHash();

        assertThat(first).hasSize(64).isEqualTo(second);
    }

    @Test
    void rcT3_permitChange_changesTheHash() {
        String withoutPermit = hash(List.of(dieselInside), true);

        when(permitRepository.findByVehicleIdAndBranchZoneIdAndBranchId(7L, 1L, BRANCH))
                .thenReturn(List.of(VehiclePermit.builder()
                        .id(41L)
                        .validFrom(LocalDate.of(2027, 1, 1))
                        .validTo(LocalDate.of(2027, 12, 31))
                        .build()));
        String withPermit = hash(List.of(dieselInside), true);

        assertThat(withPermit).isNotEqualTo(withoutPermit);
    }

    @Test
    void rcT3_templateVersionChange_changesTheHash() {
        String version2 = hash(List.of(dieselInside), true);

        template.setVersion(3);
        stubRules(3);
        String version3 = hash(List.of(dieselInside), true);

        assertThat(version3).isNotEqualTo(version2);
    }

    @Test
    void rcT3_vehicleAttributeChange_changesTheHash() {
        String diesel = hash(List.of(dieselInside), true);
        String electric = hash(List.of(electricOutside), true);

        assertThat(electric).isNotEqualTo(diesel);
    }

    @Test
    void rcT3_disablingTheZone_changesTheHash() {
        String enabled = hash(List.of(dieselInside), true);
        String disabled = hash(List.of(dieselInside), false);

        assertThat(disabled).isNotEqualTo(enabled);
    }
}
