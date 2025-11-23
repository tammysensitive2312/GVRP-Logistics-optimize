package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.response.OrderDTO;
import org.truong.gvrp_entry_api.dto.response.PageResponse;
import org.truong.gvrp_entry_api.dto.response.VehicleDTO;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.mapper.VehicleMapper;
import org.truong.gvrp_entry_api.repository.VehicleRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {
    private final VehicleMapper vehicleMapper;
    private final VehicleRepository vehicleRepository;
    
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
}
