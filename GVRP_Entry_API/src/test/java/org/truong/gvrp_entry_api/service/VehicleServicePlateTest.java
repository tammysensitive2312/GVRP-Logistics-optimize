package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.truong.gvrp_entry_api.dto.request.VehicleInputDTO;
import org.truong.gvrp_entry_api.dto.request.VehicleUpdateDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.entity.Fleet;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ResourceConflictException;
import org.truong.gvrp_entry_api.mapper.VehicleMapper;
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.repository.FleetRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.VehicleTypeRepository;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SEC-LOW-001 LOW-T1…T4 (SEC-007, audit lead #11): the license-plate check
 * must not act as a cross-branch existence oracle before the caller's own
 * references are validated, and a duplicate plate must be a 409 conflict
 * with a branch-neutral message, never an unhandled 500.
 *
 * <p>Unit tests over mocks; the caller is branch 2. The plate repository
 * mock answers "exists" to stand for a plate registered by another branch.
 */
@ExtendWith(MockitoExtension.class)
class VehicleServicePlateTest {

    private static final Long CALLER_BRANCH = 2L;
    private static final String FOREIGN_PLATE = "TEST-B-0001";

    @Mock private VehicleMapper vehicleMapper;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private FleetRepository fleetRepository;
    @Mock private DepotRepository depotRepository;
    @Mock private VehicleTypeRepository typeRepository;

    @InjectMocks
    private VehicleService service;

    private static VehicleInputDTO input(String plate) {
        return VehicleInputDTO.builder()
                .vehicleLicensePlate(plate)
                .vehicleTypeId(4L)
                .startDepotId(3L)
                .endDepotId(3L)
                .build();
    }

    private static Fleet ownFleet() {
        return Fleet.builder().id(5L).branch(Branch.builder().id(CALLER_BRANCH).build()).build();
    }

    private void ownReferencesResolve() {
        when(fleetRepository.findByIdAndBranchId(5L, CALLER_BRANCH)).thenReturn(Optional.of(ownFleet()));
        when(depotRepository.findByIdAndBranchId(3L, CALLER_BRANCH))
                .thenReturn(Optional.of(Depot.builder().id(3L).build()));
        when(typeRepository.findByIdAndBranchId(4L, CALLER_BRANCH))
                .thenReturn(Optional.of(VehicleType.builder().id(4L).build()));
    }

    // ---------- LOW-T1: no oracle before branch-scoped validation ----------

    @Test
    void createVehicle_existingPlate_foreignFleet_sameAnswerAsNewPlate() {
        lenient().when(vehicleRepository.existsByVehicleLicensePlate(FOREIGN_PLATE)).thenReturn(true);

        assertThatThrownBy(() -> service.createVehicle(input(FOREIGN_PLATE), 999L, CALLER_BRANCH))
                .isInstanceOf(DataInvalidException.class)
                .satisfies(ex -> {
                    var detail = ((DataInvalidException) ex).getErrors().get(0);
                    assertThat(detail.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
                    assertThat(detail.getResource()).isEqualTo(AppConstant.FLEET);
                });

        verify(vehicleRepository, never()).existsByVehicleLicensePlate(anyString());
        verify(vehicleRepository, never()).save(any());
    }

    // ---------- LOW-T2: duplicate with valid own references → conflict ----------

    @Test
    void createVehicle_existingPlate_ownReferences_conflict() {
        ownReferencesResolve();
        when(vehicleRepository.existsByVehicleLicensePlate(FOREIGN_PLATE)).thenReturn(true);

        assertThatThrownBy(() -> service.createVehicle(input(FOREIGN_PLATE), 5L, CALLER_BRANCH))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("License plate is not available.");

        verify(vehicleRepository, never()).save(any());
    }

    // ---------- LOW-T3: unique-key race on insert → conflict ----------

    @Test
    void createVehicle_saveRaceOnUniqueKey_conflict() {
        ownReferencesResolve();
        when(vehicleRepository.existsByVehicleLicensePlate("TEST-NEW-1")).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThatThrownBy(() -> service.createVehicle(input("TEST-NEW-1"), 5L, CALLER_BRANCH))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("License plate is not available.");
    }

    // ---------- LOW-T4: update path ----------

    private static VehicleUpdateDTO plateUpdate(String plate) {
        VehicleUpdateDTO update = new VehicleUpdateDTO();
        update.setVehicleLicensePlate(plate);
        return update;
    }

    @Test
    void updateVehicle_plateTakenByOtherVehicle_conflict() {
        Vehicle vehicle = Vehicle.builder().id(7L).fleet(ownFleet()).vehicleLicensePlate("TEST-OWN-7").build();
        when(vehicleRepository.findByIdAndFleetBranchId(7L, CALLER_BRANCH)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.existsByVehicleLicensePlateAndIdNot(FOREIGN_PLATE, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateVehicle(7L, plateUpdate(FOREIGN_PLATE), CALLER_BRANCH))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("License plate is not available.");

        verify(vehicleMapper, never()).updateEntityFromDTO(any(VehicleUpdateDTO.class), any());
        verify(vehicleRepository, never()).save(any());
        verify(vehicleRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateVehicle_keepsOwnPlate_saves() {
        Vehicle vehicle = Vehicle.builder().id(7L).fleet(ownFleet()).vehicleLicensePlate("TEST-OWN-7").build();
        when(vehicleRepository.findByIdAndFleetBranchId(7L, CALLER_BRANCH)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.existsByVehicleLicensePlateAndIdNot("test-own-7", 7L)).thenReturn(false);
        when(vehicleRepository.saveAndFlush(vehicle)).thenReturn(vehicle);

        service.updateVehicle(7L, plateUpdate("test-own-7"), CALLER_BRANCH);

        verify(vehicleRepository).saveAndFlush(vehicle);
    }

    @Test
    void updateVehicle_flushRaceOnUniqueKey_conflict() {
        Vehicle vehicle = Vehicle.builder().id(7L).fleet(ownFleet()).vehicleLicensePlate("TEST-OWN-7").build();
        when(vehicleRepository.findByIdAndFleetBranchId(7L, CALLER_BRANCH)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.existsByVehicleLicensePlateAndIdNot("TEST-NEW-7", 7L)).thenReturn(false);
        when(vehicleRepository.saveAndFlush(vehicle))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThatThrownBy(() -> service.updateVehicle(7L, plateUpdate("TEST-NEW-7"), CALLER_BRANCH))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("License plate is not available.");
    }
}
