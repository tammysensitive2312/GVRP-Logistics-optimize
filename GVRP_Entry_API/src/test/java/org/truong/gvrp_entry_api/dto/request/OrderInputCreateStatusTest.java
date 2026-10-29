package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BF-T1 (BROWSER-FIX-001, E1): a create payload without {@code status} binds
 * to a null status and passes the {@code OnCreate} group. The old field
 * initializer filled {@code SCHEDULED}, so every create failed {@code @Null}.
 */
class OrderInputCreateStatusTest {

    private static final ObjectMapper JSON = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .findAndRegisterModules();
    private static final String VALID = """
            {"order_code":"ORD-1","customer_name":"Khach","demand":12.5,"delivery_date":"2026-10-29"%s}
            """;

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void open() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    void bfT1_createWithoutStatus_bindsNullAndHasNoViolation() throws Exception {
        OrderInputDTO dto = JSON.readValue(VALID.formatted(""), OrderInputDTO.class);

        Set<ConstraintViolation<OrderInputDTO>> violations =
                validator.validate(dto, OrderInputDTO.OnCreate.class);

        assertThat(dto.getStatus()).isNull();
        assertThat(violations).isEmpty();
    }

    @Test
    void bfT1_createWithStatus_hasOneViolationOnStatus() throws Exception {
        OrderInputDTO dto = JSON.readValue(VALID.formatted(",\"status\":\"SCHEDULED\""), OrderInputDTO.class);

        Set<ConstraintViolation<OrderInputDTO>> violations =
                validator.validate(dto, OrderInputDTO.OnCreate.class);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("status");
    }
}
