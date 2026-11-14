package com.claimguard.api.dto;

import com.claimguard.domain.LineOfBusiness;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Incoming claim. Unknown fields (e.g. the generator's "label") are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimRequest(
        @NotBlank String claimId,
        @NotBlank String policyNumber,
        @NotNull LocalDate policyStartDate,
        @NotNull @Positive BigDecimal sumInsured,
        @NotNull LineOfBusiness lineOfBusiness,
        String vehicleRegNo,
        @NotBlank String providerId,
        @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit mobile number") String claimantMobile,
        @NotNull LocalDate lossDate,
        @NotNull Instant reportedAt,
        @NotNull @Positive BigDecimal claimedAmount) {

    @JsonIgnore
    @AssertTrue(message = "vehicleRegNo is required for MOTOR claims")
    public boolean isVehicleRegNoValid() {
        return lineOfBusiness != LineOfBusiness.MOTOR
                || (vehicleRegNo != null && !vehicleRegNo.isBlank());
    }

    /** Deliberately omits claimantMobile so the number can never reach a log line. */
    @Override
    public String toString() {
        return "ClaimRequest[claimId=" + claimId + ", policyNumber=" + policyNumber + "]";
    }
}
