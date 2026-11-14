package com.claimguard.persistence;

import jakarta.persistence.Embeddable;

@Embeddable
public class TriggeredRuleRow {

    private String code;
    private int weight;
    private String reason;

    protected TriggeredRuleRow() { }

    public TriggeredRuleRow(String code, int weight, String reason) {
        this.code = code;
        this.weight = weight;
        this.reason = reason;
    }

    public String getCode() { return code; }

    public int getWeight() { return weight; }

    public String getReason() { return reason; }
}
