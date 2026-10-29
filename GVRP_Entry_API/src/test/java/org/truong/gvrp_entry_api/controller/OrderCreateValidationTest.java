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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.truong.gvrp_entry_api.exception.GlobalExceptionHandler;
import org.truong.gvrp_entry_api.service.orders.OrderImportService;
import org.truong.gvrp_entry_api.service.orders.OrderService;
import org.truong.gvrp_entry_api.util.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BF-T2 (BROWSER-FIX-001, E2): a bean-validation failure answers 400 with one
 * error per field instead of 500, without echoing rejected values.
 */
@ExtendWith(MockitoExtension.class)
class OrderCreateValidationTest {

    private static final String PHONE = "0912 345 678";

    @Mock
    private OrderImportService orderImportService;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController controller;

    private MockMvc mockMvc() {
        // Production SNAKE_CASE wire contract (WebConfig).
        ObjectMapper snake = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(snake))
                .build();
    }

    @Test
    void bfT2_blankOrderCode_is400WithTheFieldMessage_andNoRejectedValue() throws Exception {
        String body = """
                {"order_code":"","customer_name":"Khach","customer_phone":"%s",
                 "demand":12.5,"delivery_date":"2026-10-29"}
                """.formatted(PHONE);

        MvcResult result = mockMvc().perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value("orderCode"))
                .andExpect(jsonPath("$.errors[0].message").value("Order code is required"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(PHONE);
        verifyNoInteractions(orderService);
    }

    @Test
    void bfT2_twoInvalidFields_giveOneErrorEach() throws Exception {
        String body = """
                {"order_code":"","customer_name":"","demand":12.5,"delivery_date":"2026-10-29"}
                """;

        mockMvc().perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        verifyNoInteractions(orderService);
    }
}
