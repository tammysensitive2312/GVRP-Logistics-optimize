package org.truong.gvrp_entry_api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.truong.gvrp_entry_api.dto.request.OrderImportRequest;
import org.truong.gvrp_entry_api.dto.response.ImportResultDTO;
import org.truong.gvrp_entry_api.exception.GlobalExceptionHandler;
import org.truong.gvrp_entry_api.service.orders.OrderImportService;
import org.truong.gvrp_entry_api.service.orders.OrderService;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UO-T7 (UX-ORDERS-001, S2): the multipart import form binds `deliveryDate` to a LocalDate. */
@ExtendWith(MockitoExtension.class)
class OrderImportBindingTest {

    @Mock
    private OrderImportService orderImportService;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController controller;

    @Test
    void uoT7_deliveryDateFormField_bindsToLocalDate() throws Exception {
        when(orderImportService.createOrdersByImportRequest(any(), any()))
                .thenReturn(ImportResultDTO.builder().success(true).importedCount(0).build());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(multipart("/api/v1/orders/import")
                        .param("textData", "ORD-1|A||1 Trang Tien|1|||1||5")
                        .param("deliveryDate", "2026-10-29")
                        .param("serviceTime", "5")
                        .param("overwriteExisting", "false")
                        .param("skipValidationErrors", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<OrderImportRequest> captor = ArgumentCaptor.forClass(OrderImportRequest.class);
        verify(orderImportService).createOrdersByImportRequest(captor.capture(), any());
        OrderImportRequest request = captor.getValue();
        assertThat(request.getDeliveryDate()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(request.getServiceTime()).isEqualTo(5);
        assertThat(request.getSkipValidationErrors()).isTrue();
        assertThat(request.getOverwriteExisting()).isFalse();
    }
}
