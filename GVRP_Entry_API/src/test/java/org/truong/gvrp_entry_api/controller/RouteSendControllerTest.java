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
import org.truong.gvrp_entry_api.dto.response.RouteSendDTO;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.exception.ForbiddenException;
import org.truong.gvrp_entry_api.exception.GlobalExceptionHandler;
import org.truong.gvrp_entry_api.service.RouteSendService;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Envelope tests for dispatch endpoints (no Spring context).
 */
@ExtendWith(MockitoExtension.class)
class RouteSendControllerTest {

    @Mock
    private RouteSendService routeSendService;

    @InjectMocks
    private RouteSendController sendController;

    @InjectMocks
    private DriverController driverController;

    private MockMvc sendMvc() {
        // Same SNAKE_CASE wire contract as production WebConfig: proves the
        // frontend snake_case payload deserializes (regression for the
        // camelCase "driverUserId" 500).
        ObjectMapper snake = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return MockMvcBuilders.standaloneSetup(sendController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(snake))
                .build();
    }

    private MockMvc driverMvc() {
        return MockMvcBuilders.standaloneSetup(driverController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static final String SEND_BODY = """
            {"driver_user_id":2,"solution_id":9}""";

    @Test
    void send_ok_returnsRecord() throws Exception {
        when(routeSendService.sendRoute(eq(21L), any())).thenReturn(
                RouteSendDTO.builder().id(5L).status("CURRENT").build());

        sendMvc().perform(post("/api/v1/routes/21/send")
                        .contentType(MediaType.APPLICATION_JSON).content(SEND_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void send_nonPlanner_returns403() throws Exception {
        when(routeSendService.sendRoute(eq(21L), any())).thenThrow(
                new ForbiddenException("PLANNER role required"));

        sendMvc().perform(post("/api/v1/routes/21/send")
                        .contentType(MediaType.APPLICATION_JSON).content(SEND_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("0040301"));
    }

    @Test
    void driverRoutes_ok() throws Exception {
        when(routeSendService.mySends()).thenReturn(List.of(
                RouteSendDTO.builder().id(1L).status("CURRENT").build()));

        driverMvc().perform(get("/api/v1/driver/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CURRENT"));
    }

    @Test
    void drivers_nonPlanner_returns403() throws Exception {
        when(routeSendService.listDrivers()).thenThrow(
                new ForbiddenException("PLANNER role required"));

        driverMvc().perform(get("/api/v1/drivers"))
                .andExpect(status().isForbidden());
    }

    @Test
    void routeSends_ok_returnsHistory() throws Exception {
        when(routeSendService.routeSends(eq(21L))).thenReturn(List.of(
                RouteSendDTO.builder().id(2L).status("CURRENT").build(),
                RouteSendDTO.builder().id(1L).status("SUPERSEDED").build()));

        sendMvc().perform(get("/api/v1/routes/21/sends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CURRENT"))
                .andExpect(jsonPath("$[1].status").value("SUPERSEDED"));
    }

    @Test
    void send_badRequest_returns400() throws Exception {        when(routeSendService.sendRoute(eq(21L), any())).thenThrow(
                new DataInvalidException(List.of(ErrorDetail.builder()
                        .code(ErrorCode.VALIDATION_ERROR.getCode()).message("bad").build())));

        sendMvc().perform(post("/api/v1/routes/21/send")
                        .contentType(MediaType.APPLICATION_JSON).content(SEND_BODY))
                .andExpect(status().isBadRequest());
    }
}
