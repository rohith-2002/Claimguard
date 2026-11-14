package com.claimguard.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public record TriggeredRule(String code, int weight, String reason) { }
