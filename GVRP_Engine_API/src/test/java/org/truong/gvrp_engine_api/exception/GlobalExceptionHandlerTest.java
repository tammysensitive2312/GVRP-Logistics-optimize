package org.truong.gvrp_engine_api.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@link GlobalExceptionHandler} produces the unified Entry-compatible
 * {@code {"errors": [...]}} envelope across domain, validation, HTTP/MVC, and 500 paths.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    record SamplePayload(
            @NotBlank(message = "name is required") String name,
            boolean validPair
    ) {
        @AssertTrue(message = "pair invariant violated")
        public boolean isPairValid() {
            return validPair;
        }
    }

    @RestController
    @RequestMapping("/test/errors")
    static class StubController {

        @PostMapping("/valid")
        String valid(@Valid @RequestBody SamplePayload payload) {
            return "ok";
        }

        @GetMapping("/illegal-arg")
        String illegalArg() {
            throw new IllegalArgumentException("Bad argument detail");
        }

        @GetMapping("/status/{code}")
        String status(@PathVariable int code) {
            throw new ResponseStatusException(HttpStatus.valueOf(code), "Reason for " + code);
        }

        @GetMapping("/param")
        String param(@RequestParam String q) {
            return q;
        }

        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("secret internal stack hint");
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void illegalArgumentProducesUnified400AndOmitsNullFields() throws Exception {
        mvc.perform(get("/test/errors/illegal-arg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("0040001"))
                .andExpect(jsonPath("$.errors[0].message").value("Bad argument detail"))
                .andExpect(jsonPath("$.errors[0].resource").doesNotExist())
                .andExpect(jsonPath("$.errors[0].field").doesNotExist());
    }

    @Test
    void validationHandlesBothFieldErrorAndObjectErrorWithoutClassCast() throws Exception {
        mvc.perform(post("/test/errors/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"validPair\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].code", hasItem("0040001")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("name is required")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("pair invariant violated")));
    }

    @Test
    void malformedJsonReturnsNeutral400Message() throws Exception {
        mvc.perform(post("/test/errors/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("0040001"))
                .andExpect(jsonPath("$.errors[0].message").value("Malformed request body."));
    }

    @Test
    void methodNotAllowedReturns405Envelope() throws Exception {
        mvc.perform(get("/test/errors/valid"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errors[0].code").value("0040501"))
                .andExpect(jsonPath("$.errors[0].message").value("Method not allowed."));
    }

    @Test
    void unsupportedMediaTypeReturns415Envelope() throws Exception {
        mvc.perform(post("/test/errors/valid")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errors[0].code").value("0041501"))
                .andExpect(jsonPath("$.errors[0].message").value("Unsupported media type."));
    }

    @Test
    void responseStatusExceptionsMapToMatchingCodes() throws Exception {
        mvc.perform(get("/test/errors/status/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("0040402"))
                .andExpect(jsonPath("$.errors[0].message").value("Reason for 404"));

        mvc.perform(get("/test/errors/status/409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("0040901"))
                .andExpect(jsonPath("$.errors[0].message").value("Reason for 409"));

        mvc.perform(get("/test/errors/status/413"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errors[0].code").value("0041301"))
                .andExpect(jsonPath("$.errors[0].message").value("Reason for 413"));

        mvc.perform(get("/test/errors/status/502"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errors[0].code").value("0050002"))
                .andExpect(jsonPath("$.errors[0].message").value("Reason for 502"));
    }

    @Test
    void typeMismatchReturns400InvalidValueType() throws Exception {
        mvc.perform(get("/test/errors/status/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("0040005"))
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].message").value("Invalid value type."));
    }

    @Test
    void missingRequiredParameterReturns400EmptyFieldError() throws Exception {
        mvc.perform(get("/test/errors/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("0040004"))
                .andExpect(jsonPath("$.errors[0].field").value("q"));
    }

    @Test
    void non502ServerResponseStatusSuppressesRawReason() throws Exception {
        mvc.perform(get("/test/errors/status/503"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errors[0].code").value("0050001"))
                .andExpect(jsonPath("$.errors[0].message").value("Internal server error."))
                .andExpect(jsonPath("$.errors[0].message", not(containsString("Reason for 503"))));
    }

    @Test
    void unexpectedExceptionNeverLeaksInternalMessage() throws Exception {
        mvc.perform(get("/test/errors/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errors[0].code").value("0050001"))
                .andExpect(jsonPath("$.errors[0].message").value("Internal server error."))
                .andExpect(jsonPath("$.errors[0].message", not(containsString("secret internal stack hint"))));
    }
}
