package com.claimguard.api;

import com.claimguard.alerts.AlertStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Body of PATCH /api/alerts/{id}: close an OPEN alert with a mandatory note and investigator. */
public record DispositionRequest(
        @NotNull AlertStatus status,
        @NotBlank String note,
        @NotBlank String investigator) {

    @AssertTrue(message = "status must be CONFIRMED_FRAUD or FALSE_POSITIVE")
    public boolean isClosableStatus() {
        return status == AlertStatus.CONFIRMED_FRAUD || status == AlertStatus.FALSE_POSITIVE;
    }
}
