package org.truong.gvrp_entry_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.response.RevisionDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.mapper.SolutionMapper;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SolutionService {

    private final SolutionRepository solutionRepository;
    private final SolutionMapper solutionMapper;

    @Transactional(readOnly = true)
    public SolutionDetailResponseDTO getSolutionDetail(Long id) {        Solution solution = solutionRepository.findWithDetailsById(id)
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

    /** Full revision chain (root first) for the send dialog picker. */
    @Transactional(readOnly = true)
    public List<RevisionDTO> revisionChain(Long id) {
        Solution current = solutionRepository.findById(id)
                .orElseThrow(() -> new DataInvalidException(
                        List.of(
                                ErrorDetail.builder()
                                        .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                        .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                        .resource(AppConstant.SOLUTION)
                                        .build()
                        )));
        while (current.getParentSolution() != null) {
            current = current.getParentSolution();
        }
        List<RevisionDTO> chain = new ArrayList<>();
        collectRevisions(current, chain);
        return chain;
    }

    private void collectRevisions(Solution node, List<RevisionDTO> chain) {
        chain.add(RevisionDTO.builder()
                .id(node.getId())
                .createdAt(node.getCreatedAt())
                .type(node.getType() != null ? node.getType().name() : null)
                .status(node.getStatus() != null ? node.getStatus().name() : null)
                .build());
        solutionRepository.findByParentSolutionId(node.getId()).stream()
                .sorted(Comparator.comparing(Solution::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(child -> collectRevisions(child, chain));
    }
}
