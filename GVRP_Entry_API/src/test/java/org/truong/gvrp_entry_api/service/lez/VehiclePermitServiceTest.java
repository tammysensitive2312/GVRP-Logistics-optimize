package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.request.VehiclePermitInputDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehiclePermit;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Vehicle permits (LEZ-001 P1a, VP-T1, VP-T2). The caller is branch 1; the
 * branch-scoped lookups return empty for another branch's vehicle or zone.
 */
@ExtendWith(MockitoExtension.class)
class VehiclePermitServiceTest {

    private static final Long CALLER_BRANCH = 1L;

    @Mock
    private VehiclePermitRepository permitRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private BranchZoneRepository zoneRepository;

    @InjectMocks
    private VehiclePermitService service;

    private final Branch branch = Branch.builder().id(CALLER_BRANCH).build();

    private VehiclePermitInputDTO input(LocalDate from, LocalDate to) {
        return VehiclePermitInputDTO.builder()
                .branchZoneId(7L)
                .permitNo("TEST-PERMIT-1")
                .validFrom(from)
                .validTo(to)
                .build();
    }

    private void ownVehicle() {
        when(vehicleRepository.findByIdAndFleetBranchId(5L, CALLER_BRANCH))
                .thenReturn(Optional.of(Vehicle.builder().id(5L).build()));
    }

    private void ownZone() {
        when(zoneRepository.findByIdAndBranchId(7L, CALLER_BRANCH))
                .thenReturn(Optional.of(BranchZone.builder().id(7L).branch(branch).mode(BranchZoneMode.LINKED).build()));
    }

    // ---------- VP-T1 ----------

    @Test
    void vpT1_anotherBranchVehicle_is404_andNothingIsSaved() {
        assertThatThrownBy(() -> service.createPermit(5L,
                input(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)), CALLER_BRANCH))
                .isInstanceOf(LezNotFoundException.class);
        assertThatThrownBy(() -> service.listPermits(5L, CALLER_BRANCH)).isInstanceOf(LezNotFoundException.class);
        assertThatThrownBy(() -> service.deletePermit(5L, 9L, CALLER_BRANCH)).isInstanceOf(LezNotFoundException.class);

        verify(vehicleRepository, times(3)).findByIdAndFleetBranchId(5L, CALLER_BRANCH);
        verify(vehicleRepository, never()).findById(any());
        verify(permitRepository, never()).save(any());
        verify(permitRepository, never()).delete(any());
    }

    @Test
    void vpT1_anotherBranchZone_is404_andNothingIsSaved() {
        ownVehicle();

        assertThatThrownBy(() -> service.createPermit(5L,
                input(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)), CALLER_BRANCH))
                .isInstanceOf(LezNotFoundException.class);

        verify(zoneRepository).findByIdAndBranchId(7L, CALLER_BRANCH);
        verify(zoneRepository, never()).findById(any());
        verify(permitRepository, never()).save(any());
    }

    @Test
    void vpT1_anotherBranchZoneOnUpdate_is404() {
        ownVehicle();
        when(permitRepository.findByIdAndVehicleIdAndBranchId(9L, 5L, CALLER_BRANCH))
                .thenReturn(Optional.of(VehiclePermit.builder().id(9L).build()));

        assertThatThrownBy(() -> service.updatePermit(5L, 9L,
                input(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)), CALLER_BRANCH))
                .isInstanceOf(LezNotFoundException.class);
        verify(permitRepository, never()).save(any());
    }

    // ---------- VP-T2 ----------

    @Test
    void vpT2_validFromAfterValidTo_is400() {
        ownVehicle();
        ownZone();

        assertThatThrownBy(() -> service.createPermit(5L,
                input(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 1, 31)), CALLER_BRANCH))
                .isInstanceOf(DataInvalidException.class);
        verify(permitRepository, never()).save(any());
    }

    @Test
    void vpT2_51stPermit_is400() {
        ownVehicle();
        ownZone();
        when(permitRepository.countByVehicleIdAndBranchId(5L, CALLER_BRANCH))
                .thenReturn((long) VehiclePermitService.MAX_PERMITS_PER_VEHICLE);

        assertThatThrownBy(() -> service.createPermit(5L,
                input(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)), CALLER_BRANCH))
                .isInstanceOf(DataInvalidException.class);
        verify(permitRepository, never()).save(any());
    }

    @Test
    void createPermit_ownVehicleAndZone_savesWithTheBranchAndSchedule() throws Exception {
        ownVehicle();
        ownZone();
        when(permitRepository.countByVehicleIdAndBranchId(5L, CALLER_BRANCH)).thenReturn(49L);
        when(permitRepository.save(any(VehiclePermit.class))).thenAnswer(inv -> inv.getArgument(0));
        VehiclePermitInputDTO night = input(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
        night.setSchedule(new ObjectMapper().readTree(
                "{\"type\":\"ALLOWED_ONLY\",\"intervals\":[[\"21:00\",\"06:00\"]]}"));

        service.createPermit(5L, night, CALLER_BRANCH);

        ArgumentCaptor<VehiclePermit> saved = ArgumentCaptor.forClass(VehiclePermit.class);
        verify(permitRepository).save(saved.capture());
        assertThat(saved.getValue().getBranch().getId()).isEqualTo(CALLER_BRANCH);
        assertThat(saved.getValue().getVehicle().getId()).isEqualTo(5L);
        assertThat(saved.getValue().getBranchZone().getId()).isEqualTo(7L);
        assertThat(saved.getValue().getSchedule().type()).isEqualTo(RuleSchedule.Type.ALLOWED_ONLY);
    }
}
