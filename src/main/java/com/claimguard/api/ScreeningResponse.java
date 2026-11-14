package com.claimguard.api;

import com.claimguard.rules.TriggeredRule;
import java.util.List;

public record ScreeningResponse(String claimId, int score, boolean alertRaised,
                                List<TriggeredRule> triggeredRules) { }
