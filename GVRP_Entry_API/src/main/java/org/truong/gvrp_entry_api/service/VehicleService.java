package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.VehicleInputDTO;
import org.truong.gvrp_entry_api.dto.request.VehicleUpdateDTO;
import org.truong.gvrp_entry_api.dto.response.PageResponse;
import org.truong.gvrp_entry_api.dto.response.VehicleDTO;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.entity.Fleet;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.entity.enums.VehicleStatus;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.mapper.VehicleMapper;
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.repository.FleetRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.VehicleTypeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleService {
    private final VehicleMapper vehicleMapper;
    private final VehicleRepository vehicleRepository;
    private final FleetRepository fleetRepository;
    private final DepotRepository depotRepository;
    private final VehicleTypeRepository typeRepository;

    private final ObjectMapper objectMapper;
    private final VehicleFeaturesService featuresService;
    
    @Transactional(readOnly = true)
    public PageResponse<VehicleDTO> getAllVehiclesPaginated(Long branchId, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("id").descending());
        Page<Vehicle> vehiclePage = vehicleRepository.findByFleetBranchId(branchId, pageable);
        List<VehicleDTO> content = vehicleMapper.toDTOList(vehiclePage.getContent());
        
        return PageResponse.<VehicleDTO>builder()
                .content(content)
                .pageNo(vehiclePage.getNumber())
                .pageSize(vehiclePage.getSize())
                .totalElements(vehiclePage.getTotalElements())
                .totalPages(vehiclePage.getTotalPages())
                .last(vehiclePage.isLast())
                .build();
    }

    public VehicleDTO createVehicle(VehicleInputDTO input, long fleetId) {
        if (vehicleRepository.existsByVehicleLicensePlate(input.getVehicleLicensePlate())) {
            throw new ValidationException("License plate already exists");
        }

        Fleet fleet = fleetRepository.findById(fleetId)
                .orElseThrow(() -> new ResourceNotFoundException("Fleet not found", "Fleet"));

        Depot startDepot = depotRepository.findById(input.getStartDepotId())
                .orElseThrow(() -> new ResourceNotFoundException("Start depot not found", "Depot"));
        Depot endDepot = depotRepository.findById(input.getEndDepotId())
                .orElseThrow(() -> new ResourceNotFoundException("End depot not found", "Depot"));

        VehicleType vehicleType = typeRepository.findById(input.getVehicleTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle type not found", "VehicleType"));

        Vehicle vehicle = Vehicle.builder()
                .vehicleLicensePlate(input.getVehicleLicensePlate())
                .fleet(fleet)
                .vehicleType(vehicleType)
                .startDepot(startDepot)
                .endDepot(endDepot)
                .status(VehicleStatus.AVAILABLE)
                .build();

        vehicleRepository.save(vehicle);
        VehicleDTO output = vehicleMapper.toDTO(vehicle);
        return output;
    }

    @Transactional
    public VehicleDTO updateVehicle(Long id, VehicleUpdateDTO input) {
        log.info("Updating vehicle: {}", id);

        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found", "Vehicle"));

        vehicleMapper.updateEntityFromDTO(input, vehicle);

        if (input.getStartDepotId() != null) {
            Depot startDepot = depotRepository.findById(input.getStartDepotId())
                    .orElseThrow(() -> new ResourceNotFoundException("Start depot not found", "Depot"));
            vehicle.setStartDepot(startDepot);
        }

        if (input.getEndDepotId() != null) {
            Depot endDepot = depotRepository.findById(input.getEndDepotId())
                    .orElseThrow(() -> new ResourceNotFoundException("End depot not found", "Depot"));
            vehicle.setEndDepot(endDepot);
        }

        if (input.getVehicleTypeId() != null) {
            Depot endDepot = depotRepository.findById(input.getEndDepotId())
                    .orElseThrow(() -> new ResourceNotFoundException("End depot not found", "Depot"));
            vehicle.setEndDepot(endDepot);
        }

        if (input.getStatus() != null) {
            vehicle.setStatus(input.getStatus());
        }

        vehicle = vehicleRepository.save(vehicle);

        return vehicleMapper.toDTO(vehicle);
    }
}
