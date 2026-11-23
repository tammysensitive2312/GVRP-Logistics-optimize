package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.exception.ResourceConflictException;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.mapper.VehicleMapper;
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.repository.FleetRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.VehicleTypeRepository;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

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
    
    @Transactional(readOnly = true)
    public PageResponse<VehicleDTO> getAllVehiclesPaginated(Long branchId, int pageNo, int pageSize) {
        if (pageNo < 0) {
            throw new DataInvalidException(List.of(ErrorDetail.builder()
                    .code(ErrorCode.VALIDATION_ERROR.getCode())
                    .message("Page index must not be negative")
                    .field("page")
                    .build()));
        }
        if (pageSize < 1 || pageSize > AppConstant.MAX_VEHICLE_PAGE_SIZE) {
            throw new DataInvalidException(List.of(ErrorDetail.builder()
                    .code(ErrorCode.VALIDATION_ERROR.getCode())
                    .message("Page size must be between 1 and " + AppConstant.MAX_VEHICLE_PAGE_SIZE)
                    .field("size")
                    .build()));
        }
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

    public VehicleDTO createVehicle(VehicleInputDTO input, long fleetId, Long branchId) {
        // Every referenced entity must belong to the caller's branch (tenant isolation).
        // These checks run before the plate check, so a caller without valid own
        // references gets the same answer whether or not the plate exists in
        // another branch (SEC-007).
        Fleet fleet = fleetRepository.findByIdAndBranchId(fleetId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException(AppConstant.FLEET));

        Depot startDepot = depotRepository.findByIdAndBranchId(input.getStartDepotId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException(AppConstant.DEPOT, "startDepotId"));
        Depot endDepot = depotRepository.findByIdAndBranchId(input.getEndDepotId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException(AppConstant.DEPOT, "endDepotId"));

        VehicleType vehicleType = typeRepository.findByIdAndBranchId(input.getVehicleTypeId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException(AppConstant.VEHICLE_TYPE));

        // Plates are unique across all branches (global unique key). The answer
        // is the same for a plate of the caller's branch and of another branch.
        if (vehicleRepository.existsByVehicleLicensePlate(input.getVehicleLicensePlate())) {
            throw plateNotAvailable();
        }

        Vehicle vehicle = Vehicle.builder()
                .vehicleLicensePlate(input.getVehicleLicensePlate())
                .fleet(fleet)
                .vehicleType(vehicleType)
                .startDepot(startDepot)
                .endDepot(endDepot)
                .status(VehicleStatus.AVAILABLE)
                .build();

        try {
            vehicleRepository.save(vehicle);
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert of the same plate between the check and the save.
            throw plateNotAvailable();
        }
        return vehicleMapper.toDTO(vehicle);
    }

    @Transactional
    public VehicleDTO updateVehicle(Long id, VehicleUpdateDTO input, Long branchId) {
        log.info("Updating vehicle: {}", id);

        // Every referenced entity must belong to the caller's branch (tenant isolation).
        Vehicle vehicle = vehicleRepository.findByIdAndFleetBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException(AppConstant.VEHICLE));

        if (input.getVehicleLicensePlate() != null
                && vehicleRepository.existsByVehicleLicensePlateAndIdNot(input.getVehicleLicensePlate(), id)) {
            throw plateNotAvailable();
        }

        vehicleMapper.updateEntityFromDTO(input, vehicle);

        if (input.getStartDepotId() != null) {
            Depot startDepot = depotRepository.findByIdAndBranchId(input.getStartDepotId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException(AppConstant.DEPOT, "startDepotId"));
            vehicle.setStartDepot(startDepot);
        }

        if (input.getEndDepotId() != null) {
            Depot endDepot = depotRepository.findByIdAndBranchId(input.getEndDepotId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException(AppConstant.DEPOT, "endDepotId"));
            vehicle.setEndDepot(endDepot);
        }

        if (input.getVehicleTypeId() != null) {
            VehicleType type = typeRepository.findByIdAndBranchId(input.getVehicleTypeId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException(AppConstant.VEHICLE_TYPE));
            vehicle.setVehicleType(type);
        }

        if (input.getStatus() != null) {
            vehicle.setStatus(input.getStatus());
        }

        try {
            // Flush here so a unique-key race surfaces inside this method as a
            // 409, not at commit as an unhandled 500.
            vehicle = vehicleRepository.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException e) {
            throw plateNotAvailable();
        }

        return vehicleMapper.toDTO(vehicle);
    }

    /** 409 with a branch-neutral message (SEC-007). */
    private static ResourceConflictException plateNotAvailable() {
        return new ResourceConflictException("License plate is not available.");
    }
}
