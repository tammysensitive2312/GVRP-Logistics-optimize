package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The vehicle-type payload of both web clients (LEZ-001 P1c follow-up).
 *
 * The API answers in snake_case, so the clients re-send the response field name
 * {@code vehicle_features}; the DTO keeps {@code features} as the canonical name
 * and accepts the response name through {@code @JsonAlias}.
 */
class VehicleTypeInputDeserializationTest {

    /** Mirrors WebConfig#objectMapper: snake_case, unknown fields rejected (Jackson default). */
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void readsTheClientPayloadFieldVehicleFeatures() throws Exception {
        String json = """
                {
                  "type_name": "Van 1.5t",
                  "description": "Test",
                  "vehicle_features": { "category": "VAN", "emission_factor": 0.21, "skills": [] }
                }
                """;

        VehicleTypeInputDTO input = mapper.readValue(json, VehicleTypeInputDTO.class);

        assertThat(input.getTypeName()).isEqualTo("Van 1.5t");
        assertThat(input.getFeatures()).isNotNull();
        assertThat(input.getFeatures().getEmissionFactor()).isEqualTo(0.21);
    }

    @Test
    void stillReadsTheCanonicalFieldFeatures() throws Exception {
        String json = """
                {
                  "type_name": "Van 1.5t",
                  "features": { "emission_factor": 0.21, "skills": [] }
                }
                """;

        VehicleTypeInputDTO input = mapper.readValue(json, VehicleTypeInputDTO.class);

        assertThat(input.getFeatures()).isNotNull();
        assertThat(input.getFeatures().getEmissionFactor()).isEqualTo(0.21);
    }

    @Test
    void rejectsAFieldThatIsNeitherTheAliasNorTheCanonicalName() {
        String json = """
                { "type_name": "Van 1.5t", "vehicle_feature": { "emission_factor": 0.21 } }
                """;

        assertThatThrownBy(() -> mapper.readValue(json, VehicleTypeInputDTO.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
