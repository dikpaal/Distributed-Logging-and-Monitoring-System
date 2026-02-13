package com.logging.alert.controller;

import com.logging.alert.config.AlertProperties;
import com.logging.alert.entity.AlertEntity;
import com.logging.alert.model.AlertRule;
import com.logging.alert.repository.AlertRepository;
import com.logging.alert.service.AlertRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertRepository alertRepository;

    @MockBean
    private AlertRuleService alertRuleService;

    @MockBean
    private AlertProperties alertProperties;

    private AlertEntity testAlert;
    private AlertRule testRule;

    @BeforeEach
    void setUp() {
        testAlert = new AlertEntity();
        testAlert.setId(UUID.randomUUID());
        testAlert.setRuleName("high-error-rate");
        testAlert.setServiceName("test-service");
        testAlert.setSeverity("ERROR");
        testAlert.setCount(15L);
        testAlert.setThreshold(10L);
        testAlert.setWindowSeconds(60);
        testAlert.setMessage("Alert triggered: 15 events in 60 seconds");
        testAlert.setTriggeredAt(Instant.now());

        testRule = new AlertRule("high-error-rate", null, "ERROR", 10, 60);
    }

    @Test
    void getAlerts_returnsPagedAlerts() throws Exception {
        when(alertRepository.findAllByOrderByTriggeredAtDesc(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(testAlert)));

        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].ruleName").value("high-error-rate"))
                .andExpect(jsonPath("$.content[0].count").value(15))
                .andExpect(jsonPath("$.content[0].threshold").value(10))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getAlerts_withPagination() throws Exception {
        when(alertRepository.findAllByOrderByTriggeredAtDesc(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(testAlert), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/alerts")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void getAlerts_emptyList() throws Exception {
        when(alertRepository.findAllByOrderByTriggeredAtDesc(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getRules_returnsConfiguredRules() throws Exception {
        when(alertRuleService.getAllRules()).thenReturn(List.of(testRule));

        mockMvc.perform(get("/api/v1/alerts/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("high-error-rate"))
                .andExpect(jsonPath("$[0].severity").value("ERROR"))
                .andExpect(jsonPath("$[0].threshold").value(10))
                .andExpect(jsonPath("$[0].windowSeconds").value(60));
    }

    @Test
    void getRules_multipleRules() throws Exception {
        AlertRule rule2 = new AlertRule("service-down", "order-service", "ERROR", 5, 30);
        when(alertRuleService.getAllRules()).thenReturn(List.of(testRule, rule2));

        mockMvc.perform(get("/api/v1/alerts/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("service-down"))
                .andExpect(jsonPath("$[1].serviceName").value("order-service"));
    }
}
