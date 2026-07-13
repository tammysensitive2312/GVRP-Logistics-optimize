package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.DepotInputDTO;
import org.truong.gvrp_entry_api.dto.response.DepotDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.mapper.DepotMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepotService {
    private final DepotRepository depotRepository;
    private final BranchRepository branchRepository;
    private final DepotMapper depotMapper;

    public DepotDTO createDepot(DepotInputDTO depotInputDTO, Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new DataInvalidException(
                        List.of(
                                ErrorDetail.builder()
                                        .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                        .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                        .resource(AppConstant.BRANCH)
                                        .build()
                        ))
                );

        Depot depot = depotMapper.toEntity(depotInputDTO, branch);
        depot = depotRepository.save(depot);
        return depotMapper.toDTO(depot);
    }

    @Transactional(readOnly = true)
    public List<DepotDTO> getListDepots(Long branchId) {
        List<Depot> depots = depotRepository.findByBranchId(branchId);
        List<DepotDTO> dtoList = depots.stream().map(depotMapper::toDTO).toList();
        return dtoList;
    }
}
