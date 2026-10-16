package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.request.VehicleInputDTO;
import org.truong.gvrp_entry_api.dto.request.VehicleUpdateDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.entity.Fleet;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.mapper.VehicleMapper;
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.repository.FleetRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.VehicleTypeRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tenant isolation for vehicle create/update (SEC-TENANT-001, TI-003, TI-004).
 *
 * <p>The caller is branch 2. Branch-scoped repository lookups return empty for
 * entities of another branch; the service must refuse and persist nothing.
 * These are unit tests over mocks: they prove the service uses only
 * branch-scoped lookups. The Spring Data derived queries themselves are not
 * exercised here (no database in unit tests).
 */
@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    private static final Long CALLER_BRANCH = 2L;

    @Mock
    private VehicleMapper vehicleMapper;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private FleetRepository fleetRepository;
    @Mock
    private DepotRepository depotRepository;
    @Mock
    private VehicleTypeRepository typeRepository;

    @InjectMocks
    private VehicleService service;

    private VehicleInputDTO input() {
        return VehicleInputDTO.builder()
                .vehicleLicensePlate("TEST-PLATE-1")
                .vehicleTypeId(4L)
                .startDepotId(3L)
                .endDepotId(3L)
                .build();
    }

    private Fleet ownFleet() {
        return Fleet.builder().id(5L).branch(Branch.builder().id(CALLER_BRANCH).build()).build();
    }

    // ---------- create ----------

    @Test
    void createVehicle_foreignFleet_notFound() {
        assertThrows(DataInvalidException.class,
                () -> service.createVehicle(input(), 5L, CALLER_BRANCH));

        verify(fleetRepository).findByIdAndBranchId(5L, CALLER_BRANCH);
        verify(fleetRepository, never()).findById(any());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void createVehicle_foreignDepot_notFound() {
        when(fleetRepository.findByIdAndBranchId(5L, CALLER_BRANCH)).thenReturn(Optional.of(ownFleet()));

        assertThrows(DataInvalidException.class,
                () -> service.createVehicle(input(), 5L, CALLER_BRANCH));

        verify(depotRepository).findByIdAndBranchId(3L, CALLER_BRANCH);
        verify(depotRepository, never()).findById(any());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void createVehicle_foreignVehicleType_notFound() {
        when(fleetRepository.findByIdAndBranchId(5L, CALLER_BRANCH)).thenReturn(Optional.of(ownFleet()));
        when(depotRepository.findByIdAndBranchId(3L, CALLER_BRANCH))
                .thenReturn(Optional.of(Depot.builder().id(3L).build()));

        assertThrows(DataInvalidException.class,
                () -> service.createVehicle(input(), 5L, CALLER_BRANCH));

        verify(typeRepository).findByIdAndBranchId(4L, CALLER_BRANCH);
        verify(typeRepository, never()).findById(any());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void createVehicle_ownBranch_saves() {
        when(fleetRepository.findByIdAndBranchId(5L, CALLER_BRANCH)).thenReturn(Optional.of(ownFleet()));
        when(depotRepository.findByIdAndBranchId(3L, CALLER_BRANCH))
                .thenReturn(Optional.of(Depot.builder().id(3L).build()));
        when(typeRepository.findByIdAndBranchId(4L, CALLER_BRANCH))
                .thenReturn(Optional.of(VehicleType.builder().id(4L).build()));

        service.createVehicle(input(), 5L, CALLER_BRANCH);

        verify(vehicleRepository).save(any(Vehicle.class));
    }

    // ---------- update ----------

    @Test
    void updateVehicle_foreignVehicle_notFound() {
        VehicleUpdateDTO update = new VehicleUpdateDTO();

        assertThrows(DataInvalidException.class,
                () -> service.updateVehicle(7L, update, CALLER_BRANCH));

        verify(vehicleRepository).findByIdAndFleetBranchId(7L, CALLER_BRANCH);
        verify(vehicleRepository, never()).findById(any());
        verify(vehicleMapper, never()).updateEntityFromDTO(any(), any());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void updateVehicle_foreignDepot_notFound() {
        Vehicle vehicle = Vehicle.builder().id(7L).fleet(ownFleet()).build();
        when(vehicleRepository.findByIdAndFleetBranchId(7L, CALLER_BRANCH)).thenReturn(Optional.of(vehicle));
        VehicleUpdateDTO update = new VehicleUpdateDTO();
        update.setStartDepotId(3L);

        assertThrows(DataInvalidException.class,
                () -> service.updateVehicle(7L, update, CALLER_BRANCH));

        verify(depotRepository).findByIdAndBranchId(3L, CALLER_BRANCH);
        verify(depotRepository, never()).findById(any());
        verify(vehicleRepository, never()).save(any());
    }
}
