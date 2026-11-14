package com.claimguard.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimguard.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HighAmountRatioRuleTest {

    private final HighAmountRatioRule rule = new HighAmountRatioRule(Fixtures.props());

    @ParameterizedTest(name = "claimed {0} of 1000 -> fires={1}")
    @CsvSource({"500,false", "799,false", "799.99,false", "800,true", "801,true", "1000,true"})
    void boundaryAtThreshold(String claimed, boolean fires) {
        var claim = Fixtures.claim("C1").sumInsured("1000").claimed(claimed).build();
        assertThat(rule.evaluate(claim).isPresent()).isEqualTo(fires);
    }

    @Test
    void reasonContainsActualValues() {
        var claim = Fixtures.claim("C1").sumInsured("1000").claimed("850").build();
        var hit = rule.evaluate(claim).orElseThrow();
        assertThat(hit.code()).isEqualTo("HIGH_AMOUNT_RATIO");
        assertThat(hit.weight()).isEqualTo(35);
        assertThat(hit.reason()).isEqualTo("Claimed amount is 85.0% of sum insured (limit 80%)");
    }
}
