package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.mapper.SolutionMapper;
import org.truong.gvrp_entry_api.repository.SolutionRepository;

@Service
@RequiredArgsConstructor
public class SolutionService {

    private final SolutionRepository solutionRepository;
    private final SolutionMapper solutionMapper;

    @Transactional(readOnly = true)
    public SolutionDetailResponseDTO getSolutionDetail(Long id) {
        Solution solution = solutionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found", "solution"));

        return solutionMapper.toDTO(solution);
    }
}
