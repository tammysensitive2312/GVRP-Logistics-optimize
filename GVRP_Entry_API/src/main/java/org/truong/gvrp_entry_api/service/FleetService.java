package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.FleetInputDTO;
import org.truong.gvrp_entry_api.dto.response.FleetDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Fleet;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.mapper.FleetMapper;
import org.truong.gvrp_entry_api.mapper.VehicleMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.repository.FleetRepository;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FleetService {
    private final FleetRepository fleetRepository;
    private final FleetMapper fleetMapper;
    private final BranchRepository branchRepository;
    private final DepotRepository depotRepository;

    @Transactional
    public FleetDTO createFleet(FleetInputDTO fleetInputDTO, Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Resources not found.", "branch"));

        Fleet fleet = fleetMapper.toEntity(fleetInputDTO, branch);
        var vehicleInputDTOs = fleetInputDTO.getVehicles();
        var vehicles = fleet.getVehicles();

        if (vehicleInputDTOs.size() != vehicles.size()) {
            throw new IllegalStateException("Mismatch between number of vehicle DTOs and created vehicle entities.");
        }

        for (int i = 0; i < vehicles.size(); i++) {
            var vehicleEntity = vehicles.get(i);
            var vehicleInputDTO = vehicleInputDTOs.get(i);

            var startDepotId = vehicleInputDTO.getStartDepotId();
            var endDepotId = vehicleInputDTO.getEndDepotId();

            var startDepot = depotRepository.findById(startDepotId)
                    .orElseThrow(() -> new ResourceNotFoundException("Resources not found.", "depot"));
            var endDepot = depotRepository.findById(endDepotId)
                    .orElseThrow(() -> new ResourceNotFoundException("Resources not found.", "depot"));

            vehicleEntity.setStartDepot(startDepot);
            vehicleEntity.setEndDepot(endDepot);
        }

        fleet = fleetRepository.save(fleet);
        FleetDTO fleetDTO = fleetMapper.toDTO(fleet);
        return fleetDTO;
    }

    @Transactional(readOnly = true)
    public List<FleetDTO> getFleetByBranchId(Long branchId) {

        List<Fleet> fleets = fleetRepository.findAllByBranchId(branchId);
        if (fleets.isEmpty()) {
            return Collections.emptyList();
        }
        return fleetMapper.toDTOList(fleets);
    }

    @Transactional(readOnly = true)
    public FleetDTO getFleetByIdAndBranchId(Long fleetId, Long branchId) {
        Fleet fleet = fleetRepository.findByIdAndBranchId(fleetId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Resources not found.", "fleet"));
        return fleetMapper.toDTO(fleet);
    }
}
