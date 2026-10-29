package org.truong.gvrp_entry_api.service.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.OrderDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.service.GeocodingService;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UO-T1…UO-T4 (UX-ORDERS-001, S1): Add/Edit Order without coordinates geocodes
 * the address; one-sided or ungeocodable input is refused with a field error.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceGeocodingTest {

    private static final Long BRANCH = 1L;

    @Mock private GeocodingService geocodingService;
    @Mock private BranchRepository branchRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderStatusTransitionService transitionService;

    @InjectMocks
    private OrderService service;

    private static OrderInputDTO input(Double latitude, Double longitude, String address) {
        return OrderInputDTO.builder()
                .orderCode("ORD-1").customerName("Khach").address(address)
                .latitude(latitude).longitude(longitude)
                .demand(BigDecimal.TEN).deliveryDate(LocalDate.of(2026, 10, 29))
                .build();
    }

    private void stubCreate() {
        Order entity = Order.builder().id(5L).build();
        lenient().when(branchRepository.findById(BRANCH)).thenReturn(Optional.of(Branch.builder().id(BRANCH).build()));
        lenient().when(orderMapper.toEntity(any(), any(), any())).thenReturn(entity);
        lenient().when(orderRepository.save(any())).thenReturn(entity);
        lenient().when(orderMapper.toDTO(any())).thenReturn(new OrderDTO());
    }

    private static String refusedField(Runnable call) {
        DataInvalidException ex = catchThrowableOfType(call::run, DataInvalidException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrors()).hasSize(1);
        assertThat(ex.getErrors().get(0).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        return ex.getErrors().get(0).getField();
    }

    // ---------- UO-T1 ----------

    @Test
    void uoT1_noCoordinates_areGeocodedBeforeMapping_andSaved() {
        stubCreate();
        when(geocodingService.geocode("1 Trang Tien, Ha Noi"))
                .thenReturn(new GeocodingService.GeocodeResult(21.0245, 105.8572));
        OrderInputDTO dto = input(null, null, "1 Trang Tien, Ha Noi");

        service.createOrder(dto, BRANCH);

        assertThat(dto.getLatitude()).isEqualTo(21.0245);
        assertThat(dto.getLongitude()).isEqualTo(105.8572);
        InOrder order = inOrder(geocodingService, orderMapper, orderRepository);
        order.verify(geocodingService).geocode("1 Trang Tien, Ha Noi");
        order.verify(orderMapper).toEntity(eq(dto), any(), any());
        order.verify(orderRepository).save(any());
    }

    // ---------- UO-T2 ----------

    @Test
    void uoT2_addressNotFound_isRefusedOnAddress_andNothingIsSaved() {
        stubCreate();
        when(geocodingService.geocode("nowhere")).thenReturn(null);

        assertThat(refusedField(() -> service.createOrder(input(null, null, "nowhere"), BRANCH)))
                .isEqualTo("address");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void uoT2_blankAddressWithoutCoordinates_isRefusedOnAddress_withoutGeocoding() {
        assertThat(refusedField(() -> service.createOrder(input(null, null, "  "), BRANCH)))
                .isEqualTo("address");
        verifyNoInteractions(geocodingService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void uoT2_onlyLatitude_isRefusedOnLatitude_withoutGeocoding() {
        assertThat(refusedField(() -> service.createOrder(input(21.0, null, "1 Trang Tien"), BRANCH)))
                .isEqualTo("latitude");
        assertThat(refusedField(() -> service.createOrder(input(null, 105.0, "1 Trang Tien"), BRANCH)))
                .isEqualTo("latitude");
        verifyNoInteractions(geocodingService);
        verify(orderRepository, never()).save(any());
    }

    // ---------- UO-T3 ----------

    @Test
    void uoT3_bothCoordinates_areKept_andTheGeocoderIsNeverCalled() {
        stubCreate();
        OrderInputDTO dto = input(21.0, 105.8, "1 Trang Tien, Ha Noi");

        service.createOrder(dto, BRANCH);

        assertThat(dto.getLatitude()).isEqualTo(21.0);
        verifyNoInteractions(geocodingService);
        verify(orderRepository).save(any());
    }

    // ---------- UO-T4 ----------

    @Test
    void uoT4_updateWithBothCoordinatesNull_isGeocoded() {
        Order existing = Order.builder().id(9L).build();
        when(orderRepository.findByIdAndBranchId(9L, BRANCH)).thenReturn(Optional.of(existing));
        when(orderMapper.updateEntityFromDTO(any(), eq(existing))).thenReturn(existing);
        when(geocodingService.geocode("2 Hang Bai, Ha Noi"))
                .thenReturn(new GeocodingService.GeocodeResult(21.02, 105.85));
        OrderInputDTO dto = input(null, null, "2 Hang Bai, Ha Noi");

        service.updateOrdersById(9L, BRANCH, dto);

        assertThat(dto.getLatitude()).isEqualTo(21.02);
        assertThat(dto.getLongitude()).isEqualTo(105.85);
        verify(orderRepository).save(existing);
    }

    @Test
    void uoT4_updateOfAnotherBranchOrder_isNotFound_beforeAnyGeocoding() {
        when(orderRepository.findByIdAndBranchId(9L, BRANCH)).thenReturn(Optional.empty());

        catchThrowableOfType(() -> service.updateOrdersById(9L, BRANCH, input(null, null, "2 Hang Bai")),
                DataInvalidException.class);

        verifyNoInteractions(geocodingService);
        verify(orderRepository, never()).save(any());
    }
}
