package org.truong.gvrp_entry_api.service.lez;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.MultiPolygon;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.response.OrderZoneMembershipDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ForbiddenException;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Read-only order → zone membership (LEZ-001 P1c, OZ-T1/T2). DB-free: the two
 * repositories are Mockito substitutes.
 *
 * <p>Asserts the compiler's own conventions: enabled zones only, boundary counts
 * as inside, another branch's order behaves like a missing one, and a bad id
 * list is a 400 rather than a 500.
 */
@ExtendWith(MockitoExtension.class)
class ZoneMembershipServiceTest {

    private static final Long BRANCH_A = 1L;
    private static final Long BRANCH_B = 2L;

    @Mock private OrderRepository orderRepository;
    @Mock private BranchZoneRepository branchZoneRepository;

    private ZoneMembershipService service;

    @BeforeEach
    void setUp() {
        service = new ZoneMembershipService(orderRepository, branchZoneRepository);
    }

    // ---------- fixtures ----------

    private static Branch branch(Long id) {
        return Branch.builder().id(id).build();
    }

    private static Order order(Long id, double latitude, double longitude) {
        return Order.builder()
                .id(id)
                .branch(branch(BRANCH_A))
                .orderCode("ORD-" + id)
                .location(LezFixtures.point(latitude, longitude))
                .build();
    }

    private static BranchZone linked(Long id, String templateName, MultiPolygon geometry, boolean enabled) {
        return BranchZone.builder()
                .id(id)
                .branch(branch(BRANCH_A))
                .mode(BranchZoneMode.LINKED)
                .sourceTemplate(ZoneTemplate.builder()
                        .id(id * 10)
                        .name(templateName)
                        .geometry(geometry)
                        .version(1)
                        .build())
                .enabled(enabled)
                .build();
    }

    private static BranchZone copied(Long id, String name, MultiPolygon geometry, boolean enabled) {
        return BranchZone.builder()
                .id(id)
                .branch(branch(BRANCH_A))
                .mode(BranchZoneMode.COPIED)
                .sourceTemplate(ZoneTemplate.builder()
                        .id(id * 10)
                        .name("Template of " + name)
                        .geometry(geometry)
                        .version(1)
                        .build())
                .name(name)
                .geometry(geometry)
                .enabled(enabled)
                .build();
    }

    // ---------- OZ-T1 ----------

    @Test
    void ozT1_onlyEnabledZoneCounts_boundaryIsInside_outsideIsEmpty() {
        MultiPolygon square = LezFixtures.square(106.0, 10.0, 106.1, 10.1);
        when(branchZoneRepository.findByBranchIdOrderByIdAsc(BRANCH_A))
                .thenReturn(List.of(linked(17L, "Hanoi LEZ", square, true),
                        copied(18L, "Retired LEZ", square, false)));
        when(orderRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(
                order(1L, 10.05, 106.05),
                order(2L, 10.0, 106.0),
                order(3L, 11.0, 107.0)));

        List<OrderZoneMembershipDTO> rows = service.memberships("1,2,3", BRANCH_A);

        assertThat(rows).extracting(OrderZoneMembershipDTO::getOrderId)
                .containsExactly(1L, 2L, 3L);
        assertThat(rows.get(0).getZones())
                .extracting(OrderZoneMembershipDTO.Zone::getName)
                .containsExactly("Hanoi LEZ");
        assertThat(rows.get(0).getZones().get(0).getBranchZoneId()).isEqualTo(17L);
        // The boundary counts as inside, exactly like GeoJsonGeometryParser.covers.
        assertThat(rows.get(1).getZones()).hasSize(1);
        assertThat(rows.get(2).getZones()).isEmpty();
    }

    @Test
    void ozT1_copiedZoneReportsItsOwnName() {
        MultiPolygon square = LezFixtures.square(106.0, 10.0, 106.1, 10.1);
        when(branchZoneRepository.findByBranchIdOrderByIdAsc(BRANCH_A))
                .thenReturn(List.of(copied(21L, "Copy of Hanoi", square, true)));
        when(orderRepository.findAllById(List.of(4L)))
                .thenReturn(List.of(order(4L, 10.05, 106.05)));

        List<OrderZoneMembershipDTO> rows = service.memberships("4", BRANCH_A);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getZones())
                .extracting(OrderZoneMembershipDTO.Zone::getName)
                .containsExactly("Copy of Hanoi");
        assertThat(rows.get(0).getZones().get(0).getBranchZoneId()).isEqualTo(21L);
    }

    // ---------- OZ-T2 ----------

    @Test
    void ozT2_otherBranchOrderIsOmitted() {
        when(branchZoneRepository.findByBranchIdOrderByIdAsc(BRANCH_A)).thenReturn(List.of());
        when(orderRepository.findAllById(List.of(5L))).thenReturn(List.of(
                Order.builder().id(5L).branch(branch(BRANCH_B)).orderCode("OTHER-5").build()));

        assertThat(service.memberships("5", BRANCH_A)).isEmpty();
    }

    @Test
    void ozT2_missingOrderIsOmitted() {
        when(branchZoneRepository.findByBranchIdOrderByIdAsc(BRANCH_A)).thenReturn(List.of());
        when(orderRepository.findAllById(List.of(6L))).thenReturn(List.of());

        assertThat(service.memberships("6", BRANCH_A)).isEmpty();
    }

    @Test
    void ozT2_nullBranchIs403AndReadsNothing() {
        assertThatThrownBy(() -> service.memberships("1", null))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(orderRepository, branchZoneRepository);
    }

    @Test
    void ozT2_emptyOrMissingListIs400() {
        assertThatThrownBy(() -> service.memberships("", BRANCH_A))
                .isInstanceOf(DataInvalidException.class);
        assertThatThrownBy(() -> service.memberships(null, BRANCH_A))
                .isInstanceOf(DataInvalidException.class);

        verifyNoInteractions(orderRepository, branchZoneRepository);
    }

    @Test
    void ozT2_moreThan200IdsIs400() {
        String ids = IntStream.rangeClosed(1, ZoneMembershipService.MAX_ORDERS + 1)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));

        assertThatThrownBy(() -> service.memberships(ids, BRANCH_A))
                .isInstanceOf(DataInvalidException.class);

        verifyNoInteractions(orderRepository, branchZoneRepository);
    }

    @Test
    void ozT2_200IdsIsAccepted() {
        String ids = IntStream.rangeClosed(1, ZoneMembershipService.MAX_ORDERS)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
        when(branchZoneRepository.findByBranchIdOrderByIdAsc(BRANCH_A)).thenReturn(List.of());
        when(orderRepository.findAllById(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());

        assertThat(service.memberships(ids, BRANCH_A)).isEmpty();
    }

    @Test
    void ozT2_malformedListIs400() {
        assertThatThrownBy(() -> service.memberships("1,two,3", BRANCH_A))
                .isInstanceOf(DataInvalidException.class);
        assertThatThrownBy(() -> service.memberships("1,,3", BRANCH_A))
                .isInstanceOf(DataInvalidException.class);

        verifyNoInteractions(orderRepository, branchZoneRepository);
    }
}
