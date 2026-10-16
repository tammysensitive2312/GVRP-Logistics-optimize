package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.request.FleetInputDTO;
import org.truong.gvrp_entry_api.dto.request.VehicleInputDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Fleet;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.mapper.FleetMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.repository.FleetRepository;
import org.truong.gvrp_entry_api.repository.VehicleTypeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tenant isolation for fleet creation (SEC-TENANT-001, TI-005).
 * The caller is branch 2; depots and vehicle types must come from branch 2.
 */
@ExtendWith(MockitoExtension.class)
class FleetServiceTest {

    private static final Long CALLER_BRANCH = 2L;

    @Mock
    private FleetRepository fleetRepository;
    @Mock
    private FleetMapper fleetMapper;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private DepotRepository depotRepository;
    @Mock
    private VehicleTypeRepository typeRepository;

    @InjectMocks
    private FleetService service;

    private FleetInputDTO input() {
        VehicleInputDTO vehicle = VehicleInputDTO.builder()
                .vehicleLicensePlate("TEST-PLATE-2")
                .vehicleTypeId(4L)
                .startDepotId(3L)
                .endDepotId(3L)
                .build();
        return FleetInputDTO.builder().fleetName("test fleet").vehicles(List.of(vehicle)).build();
    }

    private void stubBranchAndMapping(FleetInputDTO dto) {
        Branch branch = Branch.builder().id(CALLER_BRANCH).build();
        Fleet fleet = Fleet.builder().branch(branch)
                .vehicles(new ArrayList<>(List.of(Vehicle.builder().build()))).build();
        when(branchRepository.findById(CALLER_BRANCH)).thenReturn(Optional.of(branch));
        when(fleetMapper.toEntity(dto, branch)).thenReturn(fleet);
    }

    @Test
    void createFleet_foreignVehicleType_notFound() {
        FleetInputDTO dto = input();
        stubBranchAndMapping(dto);

        assertThrows(DataInvalidException.class, () -> service.createFleet(dto, CALLER_BRANCH));

        verify(typeRepository).findByIdAndBranchId(4L, CALLER_BRANCH);
        verify(typeRepository, never()).findById(any());
        verify(fleetRepository, never()).save(any());
    }

    @Test
    void createFleet_foreignDepot_notFound() {
        FleetInputDTO dto = input();
        stubBranchAndMapping(dto);
        when(typeRepository.findByIdAndBranchId(4L, CALLER_BRANCH))
                .thenReturn(Optional.of(VehicleType.builder().id(4L).build()));

        assertThrows(DataInvalidException.class, () -> service.createFleet(dto, CALLER_BRANCH));

        verify(depotRepository).findByIdAndBranchId(3L, CALLER_BRANCH);
        verify(depotRepository, never()).findById(any());
        verify(fleetRepository, never()).save(any());
    }
}
