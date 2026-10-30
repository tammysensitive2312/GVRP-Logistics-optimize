package org.truong.gvrp_entry_api.service.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.service.GeocodingService;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTimeWindowTest {

    @Mock private GeocodingService geocodingService;
    @Mock private BranchRepository branchRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderMapper orderMapper;

    private OrderService newService() {
        return new OrderService(geocodingService, branchRepository, orderRepository, orderMapper, null);
    }

    @Test
    void createOrder_singleTimeWindowStart_throws() {
        OrderInputDTO dto = new OrderInputDTO();
        dto.setLatitude(21.0);
        dto.setLongitude(105.0);
        dto.setTimeWindowStart(LocalTime.of(8, 0));
        // timeWindowEnd null

        assertThatThrownBy(() -> newService().createOrder(dto, 1L))
                .isInstanceOf(DataInvalidException.class)
                .satisfies(ex -> {
                    DataInvalidException die = (DataInvalidException) ex;
                    assertThat(die.getErrors().get(0).getMessage())
                            .isEqualTo("Time Window must have both start and end times, or neither.");
                });
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrder_singleTimeWindowEnd_throws() {
        OrderInputDTO dto = new OrderInputDTO();
        dto.setLatitude(21.0);
        dto.setLongitude(105.0);
        dto.setTimeWindowEnd(LocalTime.of(10, 0));

        assertThatThrownBy(() -> newService().updateOrdersById(1L, 1L, dto))
                .isInstanceOf(DataInvalidException.class)
                .satisfies(ex -> assertThat(((DataInvalidException) ex).getErrors().get(0).getMessage())
                        .isEqualTo("Time Window must have both start and end times, or neither."));
        verify(orderRepository, never()).save(any());
    }
}
