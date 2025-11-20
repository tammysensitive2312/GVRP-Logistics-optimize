package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.DepotInputDTO;
import org.truong.gvrp_entry_api.dto.response.DepotDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.mapper.DepotMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.DepotRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepotService {
    private final DepotRepository depotRepository;
    private final BranchRepository branchRepository;
    private final DepotMapper depotMapper;

    public DepotDTO createDepot(DepotInputDTO depotInputDTO, Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Resources not found.", "branch"));

        Depot depot = depotMapper.toEntity(depotInputDTO, branch);
        depot = depotRepository.save(depot);
        DepotDTO depotDTO = depotMapper.toDTO(depot);
        return depotDTO;
    }

    @Transactional(readOnly = true)
    public List<DepotDTO> getListDepots(Long branchId) {
        List<Depot> depots = depotRepository.findByBranchId(branchId);
        List<DepotDTO> dtoList = depots.stream().map(depotMapper::toDTO).toList();
        return dtoList;
    }
}
