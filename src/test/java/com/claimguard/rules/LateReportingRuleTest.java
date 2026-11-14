package com.claimguard.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimguard.Fixtures;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LateReportingRuleTest {

    private static final LocalDate LOSS = LocalDate.of(2026, 1, 1);
    private final LateReportingRule rule = new LateReportingRule(Fixtures.props());

    @ParameterizedTest(name = "reported {0} days after loss -> fires={1}")
    @CsvSource({"0,false", "14,false", "15,false", "16,true", "40,true"})
    void boundaryAtThreshold(int days, boolean fires) {
        // late in the day on purpose: the rule compares calendar dates, so 15 days stays "not late"
        var reportedAt = LOSS.plusDays(days).atTime(23, 59).toInstant(ZoneOffset.UTC);
        var claim = Fixtures.claim("C1").loss(LOSS).reportedAt(reportedAt).build();
        assertThat(rule.evaluate(claim).isPresent()).isEqualTo(fires);
    }

    @Test
    void reasonContainsActualValues() {
        var reportedAt = LOSS.plusDays(22).atTime(9, 0).toInstant(ZoneOffset.UTC);
        var hit = rule.evaluate(Fixtures.claim("C1").loss(LOSS).reportedAt(reportedAt).build()).orElseThrow();
        assertThat(hit.code()).isEqualTo("LATE_REPORTING");
        assertThat(hit.weight()).isEqualTo(20);
        assertThat(hit.reason()).isEqualTo("Claim reported 22 days after loss (limit 15)");
    }
}
