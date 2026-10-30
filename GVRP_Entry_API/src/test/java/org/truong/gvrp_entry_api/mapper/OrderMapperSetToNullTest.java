package org.truong.gvrp_entry_api.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperSetToNullTest {

    @Test
    void updateEntityFromDTO_clearsFourFields_keepsStatusAndPriority() {
        OrderMapperImpl mapper = new OrderMapperImpl();
        ReflectionTestUtils.setField(mapper, "geometryMapper", new GeometryMapper());

        Order entity = Order.builder()
                .id(1L)
                .branch(Branch.builder().id(1L).name("B").build())
                .orderCode("O1")
                .customerName("C")
                .customerPhone("0900")
                .address("addr")
                .demand(new java.math.BigDecimal("1"))
                .timeWindowStart(LocalTime.of(8, 0))
                .timeWindowEnd(LocalTime.of(10, 0))
                .status(OrderStatus.SCHEDULED)
                .priority(2)
                .deliveryNotes("notes")
                .deliveryDate(java.time.LocalDate.of(2026, 1, 1))
                .build();

        OrderInputDTO dto = new OrderInputDTO();
        dto.setLatitude(21.0);
        dto.setLongitude(105.0);
        // customerPhone, deliveryNotes, timeWindowStart, timeWindowEnd, status, priority left null

        mapper.updateEntityFromDTO(dto, entity);

        assertThat(entity.getCustomerPhone()).isNull();
        assertThat(entity.getDeliveryNotes()).isNull();
        assertThat(entity.getTimeWindowStart()).isNull();
        assertThat(entity.getTimeWindowEnd()).isNull();
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.SCHEDULED);
        assertThat(entity.getPriority()).isEqualTo(2);
    }
}
