package com.claimguard.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimguard.Fixtures;
import com.claimguard.rules.TriggeredRule;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class EarlyClaimRuleTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private final EarlyClaimRule rule = new EarlyClaimRule(Fixtures.props());

    @ParameterizedTest(name = "loss {0} days after start -> fires={1}")
    @CsvSource({"0,true", "6,true", "29,true", "30,true", "31,false", "90,false"})
    void boundaryAtThreshold(int days, boolean fires) {
        var claim = Fixtures.claim("C1").policyStart(START).loss(START.plusDays(days)).build();
        assertThat(rule.evaluate(claim).isPresent()).isEqualTo(fires);
    }

    @Test
    void reasonContainsActualValuesAndWeight() {
        var claim = Fixtures.claim("C1").policyStart(START).loss(START.plusDays(6)).build();
        TriggeredRule hit = rule.evaluate(claim).orElseThrow();
        assertThat(hit.code()).isEqualTo("EARLY_CLAIM");
        assertThat(hit.weight()).isEqualTo(30);
        assertThat(hit.reason()).isEqualTo("Loss occurred 6 days after policy start (limit 30)");
    }

    @Test
    void disabledRuleNeverFires() {
        var disabled = new EarlyClaimRule(Fixtures.propsWithEarlyDisabled());
        var claim = Fixtures.claim("C1").policyStart(START).loss(START.plusDays(1)).build();
        assertThat(disabled.evaluate(claim)).isEmpty();
    }

    @Test
    void changingConfiguredThresholdChangesOutcome() {
        var claim = Fixtures.claim("C1").policyStart(START).loss(START.plusDays(6)).build();
        assertThat(new EarlyClaimRule(Fixtures.props("30")).evaluate(claim)).isPresent();
        assertThat(new EarlyClaimRule(Fixtures.props("3")).evaluate(claim)).isEmpty();
    }
}
