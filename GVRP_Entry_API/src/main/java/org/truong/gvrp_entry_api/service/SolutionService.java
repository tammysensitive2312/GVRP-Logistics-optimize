package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.mapper.SolutionMapper;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SolutionService {

    private final SolutionRepository solutionRepository;
    private final SolutionMapper solutionMapper;

    @Transactional(readOnly = true)
    public SolutionDetailResponseDTO getSolutionDetail(Long id) {
        Solution solution = solutionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new DataInvalidException(
                        List.of(
                                ErrorDetail.builder()
                                        .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                        .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                        .resource(AppConstant.SOLUTION)
                                        .build()
                        )));

        return solutionMapper.toDTO(solution);
    }
}
