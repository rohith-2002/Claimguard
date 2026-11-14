package com.claimguard.api.dto;

import com.claimguard.domain.TriggeredRule;
import java.util.List;

public record ScreeningResponse(String claimId, int score, boolean alertRaised,
                                List<TriggeredRule> triggeredRules) { }
