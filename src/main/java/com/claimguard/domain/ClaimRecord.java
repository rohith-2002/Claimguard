package com.claimguard.domain;

import java.time.Instant;

/** The minimum a history store needs to remember about a screened claim. */
public record ClaimRecord(String claimId, String policyNumber, String vehicleRegNo,
                          String providerId, Instant reportedAt) { }
