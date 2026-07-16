package com.paximum.paxassist.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K30: an unhandled failure must stay on the {@code ErrorResponse} contract and leak nothing about
 * its cause. Driven through a controller that throws on purpose — the secret in the exception message
 * is what a stack trace or Spring's default {@code /error} body would have exposed.
 */
class GlobalExceptionHandlerTest {

    private static final String SECRET_IN_EXCEPTION = "jdbc:postgresql://db:5432 password=hunter2";

    @RestController
    static class BoomController {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException(SECRET_IN_EXCEPTION);
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BoomController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unhandledException_answersOnTheErrorResponseContract() throws Exception {
        mockMvc.perform(get("/boom").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Beklenmeyen bir hata oluştu. Lütfen daha sonra tekrar deneyin."))
                .andExpect(jsonPath("$.timestamp").exists())
                // The errorId is the only handle on the cause, and it resolves to a log line, not to data.
                .andExpect(jsonPath("$.details.errorId").isNotEmpty());
    }

    @Test
    void unhandledException_leaksNeitherTheMessageNorAStackTrace() throws Exception {
        MvcResult result = mockMvc.perform(get("/boom").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain(SECRET_IN_EXCEPTION)
                .doesNotContain("IllegalStateException")
                .doesNotContain("java.lang")
                .doesNotContain("at com.paximum");
    }

    @Test
    void eachFailureGetsItsOwnErrorId() throws Exception {
        // Two reports of "the same" error must be distinguishable in the logs.
        assertThat(errorIdOf(perform())).isNotEqualTo(errorIdOf(perform()));
    }

    private String perform() throws Exception {
        return mockMvc.perform(get("/boom").accept(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
    }

    private String errorIdOf(String body) {
        return com.jayway.jsonpath.JsonPath.read(body, "$.details.errorId");
    }
}
