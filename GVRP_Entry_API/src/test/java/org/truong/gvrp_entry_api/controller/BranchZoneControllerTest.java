package org.truong.gvrp_entry_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.truong.gvrp_entry_api.dto.response.BranchZoneDTO;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.exception.GlobalExceptionHandler;
import org.truong.gvrp_entry_api.service.lez.LezConflictException;
import org.truong.gvrp_entry_api.service.lez.LezNotFoundException;
import org.truong.gvrp_entry_api.service.lez.MinuteInterval;
import org.truong.gvrp_entry_api.service.lez.ZoneService;
import org.truong.gvrp_entry_api.service.lez.ZoneVerdict;
import org.truong.gvrp_entry_api.service.lez.VehiclePermitService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of the zone and permit endpoints (no Spring context): the
 * branch is read from the principal's request attribute, and the LEZ
 * exceptions map to 404 / 409 / 400 in the project error envelope (ZS-T1,
 * ZS-T4, ZS-T7, VP-T1 at the HTTP level).
 */
@ExtendWith(MockitoExtension.class)
class BranchZoneControllerTest {

    private static final Long CALLER_BRANCH = 1L;

    @Mock
    private ZoneService zoneService;
    @Mock
    private VehiclePermitService permitService;

    @InjectMocks
    private BranchZoneController zoneController;
    @InjectMocks
    private VehiclePermitController permitController;

    private MockMvc mvc() {
        // Same wire contract as the production WebConfig mapper.
        ObjectMapper snake = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return MockMvcBuilders.standaloneSetup(zoneController, permitController)
                // The JWT filter sets these attributes from the authenticated principal.
                .defaultRequest(get("/").requestAttr("branchId", CALLER_BRANCH).requestAttr("role", "PLANNER"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(snake))
                .build();
    }

    @Test
    void create_snakeCaseBody_passesPrincipalBranch_returns201() throws Exception {
        when(zoneService.createZone(any(), eq(CALLER_BRANCH))).thenReturn(
                BranchZoneDTO.builder().id(51L).mode(BranchZoneMode.LINKED).warnings(List.of()).build());

        mvc().perform(post("/api/v1/zones")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"template_id\":10,\"mode\":\"LINKED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(51))
                .andExpect(jsonPath("$.warnings").isArray());
        verify(zoneService).createZone(any(), eq(CALLER_BRANCH));
    }

    @Test
    void secondLink_returns409Envelope() throws Exception {
        when(zoneService.createZone(any(), eq(CALLER_BRANCH)))
                .thenThrow(new LezConflictException("Template 10 is already linked to this branch"));

        mvc().perform(post("/api/v1/zones")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"template_id\":10,\"mode\":\"LINKED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("0040901"));
    }

    @Test
    void otherBranchZone_returns404Envelope() throws Exception {
        when(zoneService.getZone(99L, CALLER_BRANCH)).thenThrow(new LezNotFoundException("zones"));

        mvc().perform(get("/api/v1/zones/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("0040402"))
                .andExpect(jsonPath("$.errors[0].resource").value("zones"));
    }

    @Test
    void deleteReferencedZone_returns409() throws Exception {
        org.mockito.Mockito.doThrow(new LezConflictException("Zone 7 is referenced by a vehicle permit"))
                .when(zoneService).deleteZone(7L, CALLER_BRANCH);

        mvc().perform(delete("/api/v1/zones/7")).andExpect(status().isConflict());
    }

    @Test
    void verdict_parsesDate_andWritesIntervalsAsPairs() throws Exception {
        when(zoneService.verdict(7L, 3L, LocalDate.of(2027, 3, 3), CALLER_BRANCH)).thenReturn(
                new ZoneVerdict(ZoneVerdict.Kind.PARTIAL, List.of(new MinuteInterval(360, 540)),
                        List.of("grossWeightKg"), List.of()));

        mvc().perform(get("/api/v1/zones/7/verdict").param("vehicleId", "3").param("date", "2027-03-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("PARTIAL"))
                .andExpect(jsonPath("$.forbidden[0][0]").value(360))
                .andExpect(jsonPath("$.forbidden[0][1]").value(540))
                .andExpect(jsonPath("$.missing_attributes[0]").value("grossWeightKg"));
    }

    @Test
    void verdict_badDate_returns400() throws Exception {
        mvc().perform(get("/api/v1/zones/7/verdict").param("vehicleId", "3").param("date", "03/03/2027"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("0040001"));
    }

    @Test
    void permitOnOtherBranchVehicle_returns404() throws Exception {
        when(permitService.createPermit(eq(5L), any(), eq(CALLER_BRANCH)))
                .thenThrow(new LezNotFoundException("vehicles"));

        mvc().perform(post("/api/v1/vehicles/5/permits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branch_zone_id\":7,\"valid_from\":\"2027-01-01\",\"valid_to\":\"2027-12-31\"}"))
                .andExpect(status().isNotFound());
    }
}
