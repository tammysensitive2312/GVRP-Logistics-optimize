package org.truong.gvrp_entry_api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.truong.gvrp_entry_api.dto.request.DepotInputDTO;
import org.truong.gvrp_entry_api.dto.response.DepotDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.mapper.DepotMapper;
import org.truong.gvrp_entry_api.mapper.DepotMapperImpl;
import org.truong.gvrp_entry_api.mapper.GeometryMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.DepotRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepotServiceCreateTest {

    @Mock
    private DepotRepository depotRepository;

    @Mock
    private BranchRepository branchRepository;

    @Test
    void createDepot_validInput_savesDepotWithPointAndBranch() {
        DepotMapperImpl mapper = new DepotMapperImpl();
        ReflectionTestUtils.setField(mapper, "geometryMapper", new GeometryMapper());

        when(branchRepository.findById(7L)).thenReturn(java.util.Optional.of(
                Branch.builder().id(7L).name("B").build()));
        when(depotRepository.save(any(Depot.class))).then(returnsFirstArg());

        DepotService service = new DepotService(depotRepository, branchRepository, mapper);

        DepotInputDTO input = new DepotInputDTO("Depot A", "1 Trang Tien, Ha Noi", 21.02, 105.85);
        DepotDTO result = service.createDepot(input, 7L);

        ArgumentCaptor<Depot> captor = ArgumentCaptor.forClass(Depot.class);
        org.mockito.Mockito.verify(depotRepository).save(captor.capture());
        Depot saved = captor.getValue();
        assertThat(saved.getBranch()).isNotNull();
        assertThat(saved.getBranch().getId()).isEqualTo(7L);
        assertThat(saved.getLocation()).isNotNull();
        assertThat(saved.getLocation().getY()).isEqualTo(21.02);
        assertThat(saved.getLocation().getX()).isEqualTo(105.85);
        assertThat(saved.getLocation().getSRID()).isEqualTo(4326);

        assertThat(result).isNotNull();
        assertThat(result.getLatitude()).isEqualTo(21.02);
        assertThat(result.getLongitude()).isEqualTo(105.85);
    }
}
