package com.logging.alert.controller;

import com.logging.alert.dto.AlertResponse;
import com.logging.alert.dto.AlertRuleResponse;
import com.logging.alert.dto.PagedResponse;
import com.logging.alert.entity.AlertEntity;
import com.logging.alert.repository.AlertRepository;
import com.logging.alert.service.AlertRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final AlertRepository alertRepository;
    private final AlertRuleService alertRuleService;

    public AlertController(AlertRepository alertRepository, AlertRuleService alertRuleService) {
        this.alertRepository = alertRepository;
        this.alertRuleService = alertRuleService;
    }

    @GetMapping("/alerts")
    public ResponseEntity<PagedResponse<AlertResponse>> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Fetching alerts: page={}, size={}", page, size);

        Page<AlertEntity> alertPage = alertRepository.findAllByOrderByTriggeredAtDesc(
                PageRequest.of(page, size));

        Page<AlertResponse> responsePage = alertPage.map(AlertResponse::from);

        return ResponseEntity.ok(PagedResponse.from(responsePage));
    }

    @GetMapping("/alerts/rules")
    public ResponseEntity<List<AlertRuleResponse>> getRules() {
        log.debug("Fetching alert rules");

        List<AlertRuleResponse> rules = alertRuleService.getAllRules().stream()
                .map(AlertRuleResponse::from)
                .toList();

        return ResponseEntity.ok(rules);
    }
}
