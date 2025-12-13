package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.truong.gvrp_entry_api.dto.request.VehicleTypeInputDTO;
import org.truong.gvrp_entry_api.dto.response.VehicleTypeDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.mapper.VehicleTypeMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.VehicleTypeRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleTypeService {
    private final VehicleTypeRepository typeRepository;
    private final VehicleTypeMapper typeMapper;
    private final VehicleFeaturesService featuresService;
    private final BranchRepository branchRepository;

    public VehicleTypeDTO createVehicleType(VehicleTypeInputDTO dto, Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Resources not found.", "branch"));

        var vehicleTypeEntity = typeMapper.toEntity(dto);
        vehicleTypeEntity.setVehicleFeatures(
                featuresService.toJson(dto.getVehicleFeatures())
        );
        vehicleTypeEntity.setBranch(branch);
        var savedEntity = typeRepository.save(vehicleTypeEntity);
        return typeMapper.toDTO(savedEntity);
    }

    public List<VehicleTypeDTO> getVehicleTypesByBranchId(Long branchId) {
        var vehicleTypes = typeRepository.findAllByBranchId(branchId);
        return typeMapper.toDTOList(vehicleTypes);
    }
}
