package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.truong.gvrp_entry_api.dto.request.BranchZoneCreateRequest;
import org.truong.gvrp_entry_api.dto.request.BranchZoneUpdateRequest;
import org.truong.gvrp_entry_api.dto.request.ZoneAcknowledgeRequest;
import org.truong.gvrp_entry_api.dto.request.ZoneRuleInputDTO;
import org.truong.gvrp_entry_api.dto.response.BranchZoneDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.BranchZoneRule;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.entity.ZoneRuleTemplate;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;
import org.truong.gvrp_entry_api.entity.enums.BoundaryConfidence;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ForbiddenException;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.repository.ZoneTemplateRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Branch zones (LEZ-001 P1a, ZS-T1…ZS-T7).
 *
 * <p>The caller is branch 1. Branch-scoped repository lookups return empty for
 * another branch's rows, as the derived queries do; the service must answer
 * 404 and write nothing. Unit tests over mocks, no database.
 */
@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    private static final Long CALLER_BRANCH = 1L;
    private static final Long OTHER_BRANCH_ZONE_ID = 99L;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private BranchZoneRepository zoneRepository;
    @Mock
    private BranchZoneRuleRepository zoneRuleRepository;
    @Mock
    private ZoneTemplateRepository templateRepository;
    @Mock
    private ZoneRuleTemplateRepository ruleTemplateRepository;
    @Mock
    private VehiclePermitRepository permitRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private DepotRepository depotRepository;
    @Mock
    private BranchRepository branchRepository;

    @InjectMocks
    private ZoneService service;

    private Branch branch;
    private ZoneTemplate template;
    private List<ZoneRuleTemplate> v2Rules;

    // ---------- fixtures ----------

    private static MultiPolygon square(double min, double max) throws Exception {
        return GeoJsonGeometryParser.parse(JSON.readTree(String.format(
                "{\"type\":\"Polygon\",\"coordinates\":[[[%1$s,%1$s],[%2$s,%1$s],[%2$s,%2$s],[%1$s,%2$s],[%1$s,%1$s]]]}",
                min, max)));
    }

    private static RuleSelector trucks() {
        return new RuleSelector(List.of(LegalClass.TRUCK), null, null, null, null, null);
    }

    private static RuleSchedule always() {
        return new RuleSchedule(RuleSchedule.Type.ALWAYS_FORBIDDEN, null, null);
    }

    private ZoneRuleTemplate templateRule(long id, int version) {
        return ZoneRuleTemplate.builder()
                .id(id)
                .zoneTemplate(template)
                .templateVersion(version)
                .selector(trucks())
                .schedule(always())
                .effectiveFrom(LocalDate.of(2027, 1, 1))
                .build();
    }

    @BeforeEach
    void setUp() throws Exception {
        branch = Branch.builder().id(CALLER_BRANCH).build();
        template = ZoneTemplate.builder()
                .id(10L)
                .code("TEST-ZONE")
                .name("Test zone")
                .jurisdiction("XX")
                .geometry(square(0, 10))
                .version(2)
                .boundaryConfidence(BoundaryConfidence.DIGITISED_APPROX)
                .build();
        v2Rules = List.of(templateRule(1L, 2), templateRule(2L, 2));

        lenient().when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        lenient().when(branchRepository.findById(CALLER_BRANCH)).thenReturn(Optional.of(branch));
        lenient().when(ruleTemplateRepository.findByZoneTemplateIdAndTemplateVersionOrderByIdAsc(10L, 2))
                .thenReturn(v2Rules);
        lenient().when(zoneRepository.save(any(BranchZone.class))).thenAnswer(inv -> {
            BranchZone zone = inv.getArgument(0);
            if (zone.getId() == null) {
                zone.setId(50L);
            }
            return zone;
        });
        lenient().when(zoneRepository.saveAndFlush(any(BranchZone.class))).thenAnswer(inv -> {
            BranchZone zone = inv.getArgument(0);
            zone.setId(51L);
            return zone;
        });
    }

    private BranchZone linkedZone(long id) {
        return BranchZone.builder().id(id).branch(branch).mode(BranchZoneMode.LINKED)
                .sourceTemplate(template).enabled(true).build();
    }

    private BranchZone copiedZone(long id, int copiedVersion) throws Exception {
        return BranchZone.builder().id(id).branch(branch).mode(BranchZoneMode.COPIED)
                .sourceTemplate(template).sourceTemplateVersion(copiedVersion)
                .name("Copy").geometry(square(0, 5)).enabled(true).build();
    }

    private static Depot depot(long id, double lon, double lat) {
        return Depot.builder().id(id).location(FACTORY.createPoint(new Coordinate(lon, lat))).build();
    }

    // ---------- ZS-T1 ----------

    @Test
    void zsT1_linkSameTemplateTwice_secondIs409_rowCarriesPrincipalBranch() {
        when(zoneRepository.existsByBranchIdAndSourceTemplateIdAndMode(CALLER_BRANCH, 10L, BranchZoneMode.LINKED))
                .thenReturn(false, true);
        BranchZoneCreateRequest request = new BranchZoneCreateRequest(10L, BranchZoneMode.LINKED);

        BranchZoneDTO first = service.createZone(request, CALLER_BRANCH);

        ArgumentCaptor<BranchZone> saved = ArgumentCaptor.forClass(BranchZone.class);
        verify(zoneRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getBranch().getId()).isEqualTo(CALLER_BRANCH);
        assertThat(saved.getValue().getMode()).isEqualTo(BranchZoneMode.LINKED);
        assertThat(saved.getValue().getEnabled()).isTrue();
        assertThat(saved.getValue().getGeometry()).isNull();
        assertThat(first.getEffectiveVersion()).isEqualTo(2);
        assertThat(first.getRules()).hasSize(2);

        assertThatThrownBy(() -> service.createZone(request, CALLER_BRANCH))
                .isInstanceOf(LezConflictException.class);
        verify(zoneRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void zsT1_missingPrincipalBranch_isRefusedBeforeAnyQuery() {
        assertThatThrownBy(() -> service.createZone(new BranchZoneCreateRequest(10L, BranchZoneMode.LINKED), null))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(zoneRepository, templateRepository);
    }

    // ---------- ZS-T2 ----------

    @Test
    @SuppressWarnings("unchecked")
    void zsT2_copyVersion2_copiesGeometryAndV2Rules() {
        BranchZoneDTO dto = service.createZone(new BranchZoneCreateRequest(10L, BranchZoneMode.COPIED), CALLER_BRANCH);

        ArgumentCaptor<BranchZone> savedZone = ArgumentCaptor.forClass(BranchZone.class);
        verify(zoneRepository).save(savedZone.capture());
        BranchZone zone = savedZone.getValue();
        assertThat(zone.getBranch().getId()).isEqualTo(CALLER_BRANCH);
        assertThat(zone.getMode()).isEqualTo(BranchZoneMode.COPIED);
        assertThat(zone.getSourceTemplateVersion()).isEqualTo(2);
        assertThat(zone.getName()).isEqualTo("Test zone");
        assertThat(zone.getGeometry().equalsExact(template.getGeometry())).isTrue();
        assertThat(zone.getGeometry()).isNotSameAs(template.getGeometry());
        assertThat(zone.getGeometry().getSRID()).isEqualTo(4326);

        verify(ruleTemplateRepository, atLeastOnce()).findByZoneTemplateIdAndTemplateVersionOrderByIdAsc(10L, 2);
        ArgumentCaptor<List<BranchZoneRule>> savedRules = ArgumentCaptor.forClass(List.class);
        verify(zoneRuleRepository).saveAll(savedRules.capture());
        assertThat(savedRules.getValue()).hasSize(2).allSatisfy(rule -> {
            assertThat(rule.getBranch().getId()).isEqualTo(CALLER_BRANCH);
            assertThat(rule.getBranchZone()).isSameAs(zone);
            assertThat(rule.getTemplateVersion()).isEqualTo(2);
            assertThat(rule.getSelector()).isEqualTo(trucks());
            assertThat(rule.getSchedule()).isEqualTo(always());
        });
        assertThat(dto.getSourceTemplateVersion()).isEqualTo(2);
        assertThat(dto.getUpdateAvailable()).isFalse();
    }

    // ---------- ZS-T3 ----------

    @Test
    void zsT3_editingALinkedZone_is409() throws Exception {
        when(zoneRepository.findByIdAndBranchId(7L, CALLER_BRANCH)).thenReturn(Optional.of(linkedZone(7L)));
        JsonNode polygon = JSON.readTree("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}");

        assertThatThrownBy(() -> service.replaceGeometry(7L, polygon, CALLER_BRANCH))
                .isInstanceOf(LezConflictException.class);
        assertThatThrownBy(() -> service.replaceRules(7L, List.of(), CALLER_BRANCH))
                .isInstanceOf(LezConflictException.class);
        assertThatThrownBy(() -> service.acknowledge(7L, new ZoneAcknowledgeRequest(2), CALLER_BRANCH))
                .isInstanceOf(LezConflictException.class);

        verify(zoneRepository, never()).save(any());
        verify(zoneRuleRepository, never()).deleteByZoneAndBranch(anyLong(), anyLong());
        verify(zoneRuleRepository, never()).saveAll(anyList());
    }

    // ---------- ZS-T4 ----------

    @Test
    void zsT4_otherBranchZoneId_is404ForEveryOperation_andNothingIsWritten() throws Exception {
        JsonNode polygon = JSON.readTree("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}");
        Long id = OTHER_BRANCH_ZONE_ID;

        assertThatThrownBy(() -> service.getZone(id, CALLER_BRANCH)).isInstanceOf(LezNotFoundException.class);
        assertThatThrownBy(() -> service.updateZone(id, new BranchZoneUpdateRequest(false), CALLER_BRANCH))
                .isInstanceOf(LezNotFoundException.class);
        assertThatThrownBy(() -> service.replaceGeometry(id, polygon, CALLER_BRANCH))
                .isInstanceOf(LezNotFoundException.class);
        assertThatThrownBy(() -> service.replaceRules(id, List.of(), CALLER_BRANCH))
                .isInstanceOf(LezNotFoundException.class);
        assertThatThrownBy(() -> service.acknowledge(id, new ZoneAcknowledgeRequest(2), CALLER_BRANCH))
                .isInstanceOf(LezNotFoundException.class);
        assertThatThrownBy(() -> service.deleteZone(id, CALLER_BRANCH)).isInstanceOf(LezNotFoundException.class);
        assertThatThrownBy(() -> service.verdict(id, 3L, LocalDate.of(2027, 3, 3), CALLER_BRANCH))
                .isInstanceOf(LezNotFoundException.class);

        verify(zoneRepository, times(7)).findByIdAndBranchId(id, CALLER_BRANCH);
        verify(zoneRepository, never()).findById(any());
        verify(zoneRepository, never()).save(any());
        verify(zoneRepository, never()).saveAndFlush(any());
        verify(zoneRepository, never()).delete(any());
        verify(zoneRuleRepository, never()).deleteByZoneAndBranch(anyLong(), anyLong());
        verify(zoneRuleRepository, never()).saveAll(anyList());
        verifyNoInteractions(vehicleRepository, permitRepository);
    }

    @Test
    void zsT4_verdictWithAnotherBranchVehicle_is404() {
        when(zoneRepository.findByIdAndBranchId(7L, CALLER_BRANCH)).thenReturn(Optional.of(linkedZone(7L)));

        assertThatThrownBy(() -> service.verdict(7L, 3L, LocalDate.of(2027, 3, 3), CALLER_BRANCH))
                .isInstanceOf(LezNotFoundException.class);
        verify(vehicleRepository).findByIdAndFleetBranchId(3L, CALLER_BRANCH);
        verify(vehicleRepository, never()).findById(any());
    }

    @Test
    void verdict_ownLinkedZoneAndVehicle_usesTemplateRulesAndPermits() {
        when(zoneRepository.findByIdAndBranchId(7L, CALLER_BRANCH)).thenReturn(Optional.of(linkedZone(7L)));
        VehicleType truckType = VehicleType.builder().id(4L).legalClass(LegalClass.TRUCK).capacity(1000).build();
        when(vehicleRepository.findByIdAndFleetBranchId(3L, CALLER_BRANCH))
                .thenReturn(Optional.of(Vehicle.builder().id(3L).vehicleType(truckType).build()));
        when(permitRepository.findByVehicleIdAndBranchZoneIdAndBranchId(3L, 7L, CALLER_BRANCH)).thenReturn(List.of());

        ZoneVerdict verdict = service.verdict(7L, 3L, LocalDate.of(2027, 3, 3), CALLER_BRANCH);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.ALL_DAY);
        assertThat(service.verdict(7L, 3L, LocalDate.of(2026, 12, 31), CALLER_BRANCH).kind())
                .isEqualTo(ZoneVerdict.Kind.ALLOWED);
    }

    // ---------- ZS-T5 ----------

    @Test
    void zsT5_templateBumpedToV3_acknowledgeClearsNotice_geometryAndRulesUnchanged() throws Exception {
        BranchZone copy = copiedZone(8L, 2);
        MultiPolygon ownGeometry = copy.getGeometry();
        template.setVersion(3);
        when(zoneRepository.findByIdAndBranchId(8L, CALLER_BRANCH)).thenReturn(Optional.of(copy));

        assertThat(service.getZone(8L, CALLER_BRANCH).getUpdateAvailable()).isTrue();

        assertThatThrownBy(() -> service.acknowledge(8L, new ZoneAcknowledgeRequest(2), CALLER_BRANCH))
                .isInstanceOf(DataInvalidException.class);

        BranchZoneDTO acknowledged = service.acknowledge(8L, new ZoneAcknowledgeRequest(3), CALLER_BRANCH);

        assertThat(acknowledged.getUpdateAvailable()).isFalse();
        assertThat(acknowledged.getAcknowledgedTemplateVersion()).isEqualTo(3);
        assertThat(acknowledged.getEffectiveVersion()).isEqualTo(2);
        assertThat(copy.getGeometry()).isSameAs(ownGeometry);
        assertThat(copy.getSourceTemplateVersion()).isEqualTo(2);
        verify(zoneRuleRepository, never()).deleteByZoneAndBranch(anyLong(), anyLong());
        verify(zoneRuleRepository, never()).saveAll(anyList());
        verify(ruleTemplateRepository, never()).findByZoneTemplateIdAndTemplateVersionOrderByIdAsc(10L, 3);
    }

    // ---------- ZS-T6 ----------

    @Test
    void zsT6_zoneCoveringOneOfTwoDepots_givesOneWarning() {
        when(depotRepository.findByBranchId(CALLER_BRANCH))
                .thenReturn(List.of(depot(7L, 5, 5), depot(8L, 20, 20)));

        BranchZoneDTO dto = service.createZone(new BranchZoneCreateRequest(10L, BranchZoneMode.LINKED), CALLER_BRANCH);

        assertThat(dto.getWarnings()).hasSize(1);
        assertThat(dto.getWarnings().get(0).getCode()).isEqualTo("DEPOT_IN_ZONE");
        assertThat(dto.getWarnings().get(0).getDepotId()).isEqualTo(7L);
        verify(depotRepository).findByBranchId(CALLER_BRANCH);
    }

    @Test
    void zsT6_replacingCopyGeometry_recomputesWarnings() throws Exception {
        when(zoneRepository.findByIdAndBranchId(8L, CALLER_BRANCH)).thenReturn(Optional.of(copiedZone(8L, 2)));
        when(depotRepository.findByBranchId(CALLER_BRANCH))
                .thenReturn(List.of(depot(7L, 5, 5), depot(8L, 20, 20)));
        JsonNode aroundDepot8 = JSON.readTree(
                "{\"type\":\"Polygon\",\"coordinates\":[[[15,15],[25,15],[25,25],[15,25],[15,15]]]}");

        BranchZoneDTO dto = service.replaceGeometry(8L, aroundDepot8, CALLER_BRANCH);

        assertThat(dto.getWarnings()).extracting("depotId").containsExactly(8L);
    }

    // ---------- ZS-T7 ----------

    @Test
    void zsT7_deletingAZoneReferencedByAPermit_is409() {
        when(zoneRepository.findByIdAndBranchId(7L, CALLER_BRANCH)).thenReturn(Optional.of(linkedZone(7L)));
        when(permitRepository.existsByBranchZoneIdAndBranchId(7L, CALLER_BRANCH)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteZone(7L, CALLER_BRANCH)).isInstanceOf(LezConflictException.class);

        verify(zoneRepository, never()).delete(any());
        verify(zoneRuleRepository, never()).deleteByZoneAndBranch(anyLong(), anyLong());
    }

    @Test
    void deletingAnUnreferencedZone_deletesItsRulesAndTheZone() {
        BranchZone zone = linkedZone(7L);
        when(zoneRepository.findByIdAndBranchId(7L, CALLER_BRANCH)).thenReturn(Optional.of(zone));

        service.deleteZone(7L, CALLER_BRANCH);

        verify(zoneRuleRepository).deleteByZoneAndBranch(7L, CALLER_BRANCH);
        verify(zoneRepository).delete(zone);
    }

    // ---------- rule replacement on a copy ----------

    @Test
    void replaceRules_invalidRule_isRejectedBeforeAnyWrite() throws Exception {
        when(zoneRepository.findByIdAndBranchId(8L, CALLER_BRANCH)).thenReturn(Optional.of(copiedZone(8L, 2)));
        ZoneRuleInputDTO valid = ZoneRuleInputDTO.builder()
                .selector(JSON.readTree("{\"legalClasses\":[\"TRUCK\"]}"))
                .schedule(JSON.readTree("{\"type\":\"ALWAYS_FORBIDDEN\"}"))
                .effectiveFrom(LocalDate.of(2027, 1, 1))
                .build();
        ZoneRuleInputDTO invalid = ZoneRuleInputDTO.builder()
                .selector(JSON.readTree("{}"))
                .schedule(JSON.readTree("{\"type\":\"FORBIDDEN_DURING\",\"intervals\":[[\"08:00\",\"08:00\"]]}"))
                .effectiveFrom(LocalDate.of(2027, 1, 1))
                .build();

        assertThatThrownBy(() -> service.replaceRules(8L, List.of(valid, invalid), CALLER_BRANCH))
                .isInstanceOf(DataInvalidException.class);
        verify(zoneRuleRepository, never()).deleteByZoneAndBranch(anyLong(), anyLong());
        verify(zoneRuleRepository, never()).saveAll(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceRules_validRules_replaceTheCopyRules() throws Exception {
        when(zoneRepository.findByIdAndBranchId(8L, CALLER_BRANCH)).thenReturn(Optional.of(copiedZone(8L, 2)));
        ZoneRuleInputDTO valid = ZoneRuleInputDTO.builder()
                .selector(JSON.readTree("{\"legalClasses\":[\"TRUCK\"]}"))
                .schedule(JSON.readTree("{\"type\":\"ALLOWED_ONLY\",\"intervals\":[[\"21:00\",\"06:00\"]]}"))
                .effectiveFrom(LocalDate.of(2027, 1, 1))
                .build();

        service.replaceRules(8L, List.of(valid), CALLER_BRANCH);

        verify(zoneRuleRepository).deleteByZoneAndBranch(8L, CALLER_BRANCH);
        ArgumentCaptor<List<BranchZoneRule>> rules = ArgumentCaptor.forClass(List.class);
        verify(zoneRuleRepository).saveAll(rules.capture());
        assertThat(rules.getValue()).singleElement().satisfies(rule -> {
            assertThat(rule.getBranch().getId()).isEqualTo(CALLER_BRANCH);
            assertThat(rule.getSchedule().type()).isEqualTo(RuleSchedule.Type.ALLOWED_ONLY);
        });
    }

    @Test
    void replaceRules_51Rules_isRejected() throws Exception {
        when(zoneRepository.findByIdAndBranchId(8L, CALLER_BRANCH)).thenReturn(Optional.of(copiedZone(8L, 2)));
        ZoneRuleInputDTO rule = ZoneRuleInputDTO.builder()
                .selector(JSON.readTree("{}"))
                .schedule(JSON.readTree("{\"type\":\"ALWAYS_FORBIDDEN\"}"))
                .effectiveFrom(LocalDate.of(2027, 1, 1))
                .build();

        assertThatThrownBy(() -> service.replaceRules(8L, java.util.Collections.nCopies(51, rule), CALLER_BRANCH))
                .isInstanceOf(DataInvalidException.class);
        verify(zoneRuleRepository, never()).saveAll(anyList());
    }
}
