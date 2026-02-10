package com.logging.ingestion.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({LogIngestionController.class, HealthController.class})
class LogIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpoint_returnsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void ingestLog_validPayload_returnsAccepted() throws Exception {
        String json = """
                {
                    "serviceName": "user-service",
                    "severity": "INFO",
                    "message": "User logged in",
                    "traceId": "abc-123"
                }
                """;

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void ingestLog_withIdempotencyKey_returnsAccepted() throws Exception {
        String json = """
                {
                    "serviceName": "payment-service",
                    "severity": "ERROR",
                    "message": "Payment failed"
                }
                """;

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "unique-key-123")
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void ingestLog_missingServiceName_returnsBadRequest() throws Exception {
        String json = """
                {
                    "severity": "INFO",
                    "message": "Missing service name"
                }
                """;

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestLog_missingSeverity_returnsBadRequest() throws Exception {
        String json = """
                {
                    "serviceName": "user-service",
                    "message": "Missing severity"
                }
                """;

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestLog_missingMessage_returnsBadRequest() throws Exception {
        String json = """
                {
                    "serviceName": "user-service",
                    "severity": "WARN"
                }
                """;

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestBatch_validPayload_returnsAccepted() throws Exception {
        String json = """
                [
                    {"serviceName": "user-service", "severity": "INFO", "message": "Log 1"},
                    {"serviceName": "user-service", "severity": "WARN", "message": "Log 2"}
                ]
                """;

        mockMvc.perform(post("/api/v1/logs/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.count").value(2));
    }
}
