package com.claimguard.api;

import com.claimguard.alerts.AlertService;
import com.claimguard.alerts.AlertStatus;
import com.claimguard.alerts.LineOfBusiness;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public PageResponse<AlertView> list(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) LineOfBusiness lineOfBusiness,
            @RequestParam(required = false) @Min(0) Integer minScore,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return alertService.search(status, lineOfBusiness, minScore, page, size);
    }

    @PatchMapping("/{id}")
    public AlertView dispose(@PathVariable Long id, @Valid @RequestBody DispositionRequest request) {
        return alertService.dispose(id, request);
    }
}
