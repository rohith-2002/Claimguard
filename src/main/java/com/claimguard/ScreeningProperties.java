package com.claimguard;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "screening")
public record ScreeningProperties(@Min(0) int alertThreshold,
                                  @NotNull Map<String, RuleConfig> rules) {

    public record RuleConfig(boolean enabled, @NotNull BigDecimal threshold, @Min(0) int weight) { }
}
