package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.response.RevisionDTO;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Solution;
import org.truong.gvrp_entry_api.entity.enums.SolutionStatus;
import org.truong.gvrp_entry_api.entity.enums.SolutionType;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.mapper.SolutionMapper;
import org.truong.gvrp_entry_api.repository.SolutionRepository;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tenant isolation for solution reads (SEC-TENANT-001, TI-001, TI-007).
 * A solution of branch 1 must not be visible to a caller of branch 2.
 */
@ExtendWith(MockitoExtension.class)
class SolutionServiceTest {

    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private SolutionMapper solutionMapper;

    @InjectMocks
    private SolutionService service;

    private Solution solutionOfBranch1() {
        Branch branch = Branch.builder().id(1L).build();
        return Solution.builder()
                .id(9L).branch(branch)
                .status(SolutionStatus.SUCCESS).type(SolutionType.ENGINE_GENERATED)
                .build();
    }

    private static void assertNotFound(DataInvalidException ex) {
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), ex.getErrors().get(0).getCode());
    }

    @Test
    void getSolutionDetail_otherBranch_notFound() {
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(solutionOfBranch1()));

        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> service.getSolutionDetail(9L, 2L));
        assertNotFound(ex);
        verify(solutionMapper, never()).toDTO(any());
    }

    @Test
    void getSolutionDetail_missingBranch_notFound() {
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(solutionOfBranch1()));

        assertThrows(DataInvalidException.class, () -> service.getSolutionDetail(9L, null));
        verify(solutionMapper, never()).toDTO(any());
    }

    @Test
    void getSolutionDetail_otherBranch_sameErrorAsUnknownId() {
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(solutionOfBranch1()));
        when(solutionRepository.findWithDetailsById(404L)).thenReturn(Optional.empty());

        DataInvalidException foreign = assertThrows(DataInvalidException.class,
                () -> service.getSolutionDetail(9L, 2L));
        DataInvalidException unknown = assertThrows(DataInvalidException.class,
                () -> service.getSolutionDetail(404L, 2L));
        assertEquals(unknown.getErrors().get(0).getCode(), foreign.getErrors().get(0).getCode());
        assertEquals(unknown.getErrors().get(0).getMessage(), foreign.getErrors().get(0).getMessage());
    }

    @Test
    void getSolutionDetail_ownBranch_returnsDetail() {
        Solution solution = solutionOfBranch1();
        SolutionDetailResponseDTO dto = SolutionDetailResponseDTO.builder().id(9L).build();
        when(solutionRepository.findWithDetailsById(9L)).thenReturn(Optional.of(solution));
        when(solutionMapper.toDTO(solution)).thenReturn(dto);

        assertSame(dto, service.getSolutionDetail(9L, 1L));
    }

    @Test
    void revisionChain_otherBranch_notFound() {
        when(solutionRepository.findById(9L)).thenReturn(Optional.of(solutionOfBranch1()));

        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> service.revisionChain(9L, 2L));
        assertNotFound(ex);
        verify(solutionRepository, never()).findByParentSolutionId(any());
    }

    @Test
    void revisionChain_ownBranch_returnsChain() {
        when(solutionRepository.findById(9L)).thenReturn(Optional.of(solutionOfBranch1()));
        when(solutionRepository.findByParentSolutionId(9L)).thenReturn(List.of());

        List<RevisionDTO> chain = service.revisionChain(9L, 1L);

        assertEquals(List.of(9L), chain.stream().map(RevisionDTO::getId).toList());
    }
}
