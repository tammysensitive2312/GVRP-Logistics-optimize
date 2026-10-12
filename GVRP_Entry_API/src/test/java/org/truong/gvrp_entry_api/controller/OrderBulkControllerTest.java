package org.truong.gvrp_entry_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.truong.gvrp_entry_api.dto.response.BulkUpdateOrderStatusResponse;
import org.truong.gvrp_entry_api.dto.response.OrderDTO;
import org.truong.gvrp_entry_api.dto.response.PageResponse;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.exception.GlobalExceptionHandler;
import org.truong.gvrp_entry_api.service.orders.OrderImportService;
import org.truong.gvrp_entry_api.service.orders.OrderService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Envelope + contract tests for filtered listing and bulk status updates.
 */
@ExtendWith(MockitoExtension.class)
class OrderBulkControllerTest {

    @Mock
    private OrderImportService orderImportService;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController controller;

    private MockMvc mockMvc() {
        // Production SNAKE_CASE wire contract (WebConfig).
        ObjectMapper snake = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(snake))
                .build();
    }

    @Test
    void list_forwardsFilters() throws Exception {
        // Branch id is unreadable without a request context; match anything.
        when(orderService.getAllOrdersPaginated(any(), eq(0), eq(5),
                eq(LocalDate.of(2026, 10, 11)), eq(OrderStatus.SCHEDULED), eq("hanoi")))
                .thenReturn(PageResponse.<OrderDTO>builder()
                        .content(List.of()).totalElements(0).totalPages(0).build());

        // Branch context is unreadable standalone; service mock ignores it.
        mockMvc().perform(get("/api/v1/orders")
                        .param("page", "0").param("size", "5")
                        .param("date", "2026-10-11")
                        .param("status", "scheduled")
                        .param("search", "hanoi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(0));
    }

    @Test
    void list_badStatus_returns400() throws Exception {
        mockMvc().perform(get("/api/v1/orders").param("status", "FLYING"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_badDate_returns400() throws Exception {
        mockMvc().perform(get("/api/v1/orders").param("date", "11-10-2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulk_ok_returnsPartitionedReport() throws Exception {
        when(orderService.bulkUpdateStatus(any(), eq(List.of(11L, 12L)), eq(OrderStatus.ON_ROUTE)))
                .thenReturn(BulkUpdateOrderStatusResponse.builder()
                        .updated(List.of(BulkUpdateOrderStatusResponse.UpdatedRow.builder()
                                .id(11L).status("ON_ROUTE").build()))
                        .failed(List.of(BulkUpdateOrderStatusResponse.FailedRow.builder()
                                .id(12L).orderCode("ORD-12").reason("Cannot transition").build()))
                        .build());

        mockMvc().perform(patch("/api/v1/orders/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[11,12],\"status\":\"ON_ROUTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated[0].id").value(11))
                .andExpect(jsonPath("$.failed[0].id").value(12));
    }

    @Test
    void bulk_badStatus_returns400() throws Exception {
        mockMvc().perform(patch("/api/v1/orders/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[11],\"status\":\"FLYING\"}"))
                .andExpect(status().isBadRequest());
    }
}
