package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.locationtech.jts.geom.MultiPolygon;
import org.truong.gvrp_entry_api.dto.request.BranchZoneCreateRequest;
import org.truong.gvrp_entry_api.dto.request.BranchZoneUpdateRequest;
import org.truong.gvrp_entry_api.dto.response.BranchZoneDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;
import org.truong.gvrp_entry_api.entity.enums.BoundaryConfidence;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.repository.ZoneTemplateRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BF-T8 and BF-T9 (BROWSER-FIX-001, E7): a new COPIED zone gets a name no other
 * zone of the branch uses, and a COPIED zone can be renamed (unique within the
 * branch). Every lookup stays branch-scoped. Unit tests over mocks, no database.
 */
@ExtendWith(MockitoExtension.class)
class ZoneNameTest {

    private static final Long BRANCH = 1L;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private BranchZoneRepository zoneRepository;
    @Mock private BranchZoneRuleRepository zoneRuleRepository;
    @Mock private ZoneTemplateRepository templateRepository;
    @Mock private ZoneRuleTemplateRepository ruleTemplateRepository;
    @Mock private VehiclePermitRepository permitRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private DepotRepository depotRepository;
    @Mock private BranchRepository branchRepository;

    @InjectMocks
    private ZoneService service;

    private Branch branch;
    private ZoneTemplate template;

    private static MultiPolygon square() throws Exception {
        return GeoJsonGeometryParser.parse(JSON.readTree(
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[10,0],[10,10],[0,10],[0,0]]]}"));
    }

    private static ZoneTemplate template(long id, String name) throws Exception {
        return ZoneTemplate.builder()
                .id(id).code("T" + id).name(name).jurisdiction("XX")
                .geometry(square()).version(2)
                .boundaryConfidence(BoundaryConfidence.DIGITISED_APPROX)
                .build();
    }

    @BeforeEach
    void setUp() throws Exception {
        branch = Branch.builder().id(BRANCH).build();
        template = template(10L, "Test zone");
        lenient().when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        lenient().when(branchRepository.findById(BRANCH)).thenReturn(Optional.of(branch));
        lenient().when(zoneRepository.save(any(BranchZone.class))).thenAnswer(inv -> {
            BranchZone zone = inv.getArgument(0);
            if (zone.getId() == null) {
                zone.setId(50L);
            }
            return zone;
        });
    }

    private BranchZone linked(long id, ZoneTemplate source) {
        return BranchZone.builder().id(id).branch(branch).mode(BranchZoneMode.LINKED)
                .sourceTemplate(source).enabled(true).build();
    }

    private BranchZone copied(long id, String name) throws Exception {
        return BranchZone.builder().id(id).branch(branch).mode(BranchZoneMode.COPIED)
                .sourceTemplate(template).sourceTemplateVersion(2)
                .name(name).geometry(square()).enabled(true).build();
    }

    private String copyName() {
        service.createZone(new BranchZoneCreateRequest(10L, BranchZoneMode.COPIED), BRANCH);
        ArgumentCaptor<BranchZone> saved = ArgumentCaptor.forClass(BranchZone.class);
        verify(zoneRepository).save(saved.capture());
        return saved.getValue().getName();
    }

    // ---------- BF-T8 ----------

    @Test
    void bfT8_copyWhileTheLinkedZoneExists_isNamedCopy() {
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of(linked(1L, template)));

        assertThat(copyName()).isEqualTo("Test zone (copy)");
    }

    @Test
    void bfT8_copyWhenBaseAndCopyAreTaken_isNamedCopy2_caseInsensitive() throws Exception {
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH))
                .thenReturn(List.of(copied(1L, " test ZONE "), copied(2L, "Test zone (copy)")));

        assertThat(copyName()).isEqualTo("Test zone (copy 2)");
    }

    @Test
    void bfT8_longTemplateName_staysWithin200Characters() throws Exception {
        String longName = "N".repeat(199);
        template.setName(longName);
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of(copied(1L, longName)));

        String name = copyName();

        assertThat(name).hasSizeLessThanOrEqualTo(200).endsWith(" (copy)");
        assertThat(name).startsWith("N".repeat(193));
    }

    @Test
    void bfT8_nameIsReadThroughTheBranchScopedQuery() {
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(new ArrayList<>());

        assertThat(copyName()).isEqualTo("Test zone");
        verify(zoneRepository).findByBranchIdOrderByIdAsc(BRANCH);
    }

    // ---------- BF-T9 ----------

    private void stubZone(BranchZone zone) {
        when(zoneRepository.findByIdAndBranchId(zone.getId(), BRANCH)).thenReturn(Optional.of(zone));
    }

    private static BranchZoneUpdateRequest rename(String name) {
        return new BranchZoneUpdateRequest(null, name);
    }

    @Test
    void bfT9_renameCopied_trimsAndSaves() throws Exception {
        BranchZone zone = copied(5L, "Copy");
        stubZone(zone);
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of(zone, copied(6L, "Other")));

        BranchZoneDTO dto = service.updateZone(5L, rename("  Hanoi inner  "), BRANCH);

        assertThat(zone.getName()).isEqualTo("Hanoi inner");
        assertThat(dto.getName()).isEqualTo("Hanoi inner");
        assertThat(zone.getEnabled()).isTrue();
        verify(zoneRepository).save(zone);
    }

    @Test
    void bfT9_renameToItsOwnNameInAnotherCase_isAllowed() throws Exception {
        BranchZone zone = copied(5L, "Copy");
        stubZone(zone);
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH)).thenReturn(List.of(zone));

        service.updateZone(5L, rename("COPY"), BRANCH);

        assertThat(zone.getName()).isEqualTo("COPY");
    }

    @Test
    void bfT9_blankName_isInvalid_andNothingIsSaved() throws Exception {
        stubZone(copied(5L, "Copy"));

        assertThatThrownBy(() -> service.updateZone(5L, rename("   "), BRANCH))
                .isInstanceOf(DataInvalidException.class);
        verify(zoneRepository, never()).save(any());
    }

    @Test
    void bfT9_nameOf201Characters_isInvalid_andNothingIsSaved() throws Exception {
        stubZone(copied(5L, "Copy"));

        assertThatThrownBy(() -> service.updateZone(5L, rename("x".repeat(201)), BRANCH))
                .isInstanceOf(DataInvalidException.class);
        verify(zoneRepository, never()).save(any());
    }

    @Test
    void bfT9_renamingALinkedZone_isAConflict_andNothingIsSaved() {
        stubZone(linked(5L, template));

        assertThatThrownBy(() -> service.updateZone(5L, rename("New"), BRANCH))
                .isInstanceOf(LezConflictException.class);
        verify(zoneRepository, never()).save(any());
    }

    @Test
    void bfT9_bothFieldsNull_isInvalid_andNothingIsSaved() throws Exception {
        stubZone(copied(5L, "Copy"));

        assertThatThrownBy(() -> service.updateZone(5L, new BranchZoneUpdateRequest(null, null), BRANCH))
                .isInstanceOf(DataInvalidException.class);
        verify(zoneRepository, never()).save(any());
    }

    @Test
    void bfT9_nameOfAnotherZoneInOtherCase_isInvalid_andNothingIsSaved() throws Exception {
        BranchZone zone = copied(5L, "Copy");
        stubZone(zone);
        when(zoneRepository.findByBranchIdOrderByIdAsc(BRANCH))
                .thenReturn(List.of(zone, linked(6L, template)));

        assertThatThrownBy(() -> service.updateZone(5L, rename("TEST ZONE"), BRANCH))
                .isInstanceOf(DataInvalidException.class);
        assertThat(zone.getName()).isEqualTo("Copy");
        verify(zoneRepository, never()).save(any());
    }

    @Test
    void bfT9_enabledOnly_savesEnabledAndKeepsTheName() throws Exception {
        BranchZone zone = copied(5L, "Copy");
        stubZone(zone);

        service.updateZone(5L, new BranchZoneUpdateRequest(false), BRANCH);

        assertThat(zone.getEnabled()).isFalse();
        assertThat(zone.getName()).isEqualTo("Copy");
        verify(zoneRepository).save(zone);
        verify(zoneRepository, never()).findByBranchIdOrderByIdAsc(any());
    }

    @Test
    void bfT9_otherBranchZone_isNotFound_beforeAnyNameCheck() {
        when(zoneRepository.findByIdAndBranchId(99L, BRANCH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateZone(99L, rename("New"), BRANCH))
                .isInstanceOf(LezNotFoundException.class);
        verify(zoneRepository, never()).findByBranchIdOrderByIdAsc(any());
        verify(zoneRepository, never()).save(any());
    }
}
