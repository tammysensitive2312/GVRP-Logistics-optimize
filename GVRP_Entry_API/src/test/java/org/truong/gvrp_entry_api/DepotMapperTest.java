package org.truong.gvrp_entry_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.truong.gvrp_entry_api.dto.response.DepotDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.mapper.DepotMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DepotMapperTest {

    @Autowired
    private DepotMapper depotMapper;

    @Test
    void testMapperBeanIsCreated() {
        assertThat(depotMapper).isNotNull();
    }

    @Test
    void testToDTO() {
        // Given
        Branch branch = Branch.builder()
                .id(1L)
                .name("Test Branch")
                .build();

        Depot depot = Depot.builder()
                .id(1L)
                .branch(branch)
                .name("Test Depot")
                .address("123 Test St")
                .build();

        // When
        DepotDTO dto = depotMapper.toDTO(depot);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Test Depot");
        assertThat(dto.getBranchId()).isEqualTo(1L);
        assertThat(dto.getBranchName()).isEqualTo("Test Branch");
    }
}
