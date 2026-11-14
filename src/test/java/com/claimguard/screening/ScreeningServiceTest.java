package com.claimguard.screening;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimguard.Fixtures;
import com.claimguard.rules.TriggeredRule;
import com.claimguard.alerts.AlertRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ScreeningServiceTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    @Autowired ScreeningService service;
    @Autowired AlertRepository alerts;

    @Test
    void rescreeningTheSameClaimIdKeepsOneAlertAndTheSameResult() {
        var claim = Fixtures.highScoring("IDEM-1").build();

        var first = service.screen(claim);
        var second = service.screen(claim);

        assertThat(first.alertRaised()).isTrue();
        assertThat(second).isEqualTo(first);
        assertThat(alerts.findAll().stream().filter(a -> a.getClaimId().equals("IDEM-1"))).hasSize(1);
    }

    @Test
    void repostedClaimDoesNotInflateRepeatPolicyCountForLaterClaims() {
        var earlier = Fixtures.claim("REP-A").policy("POL-REP-1").reportedAt(T0).build();
        for (int i = 0; i < 3; i++) {
            service.screen(earlier);             // same claimId three times
        }
        var later = Fixtures.claim("REP-C").policy("POL-REP-1").reportedAt(T0.plusSeconds(86_400)).build();

        var result = service.screen(later);

        // only ONE distinct earlier claim exists, threshold is 2 -> must not fire
        assertThat(result.triggeredRules()).extracting(TriggeredRule::code)
                .doesNotContain("REPEAT_POLICY_CLAIM");
    }

    @Test
    void twoDistinctEarlierClaimsDoTriggerRepeatPolicyClaim() {
        service.screen(Fixtures.claim("REP2-A").policy("POL-REP-2").reportedAt(T0).build());
        service.screen(Fixtures.claim("REP2-B").policy("POL-REP-2").reportedAt(T0.plusSeconds(3_600)).build());
        var result = service.screen(
                Fixtures.claim("REP2-C").policy("POL-REP-2").reportedAt(T0.plusSeconds(7_200)).build());

        assertThat(result.triggeredRules()).extracting(TriggeredRule::code)
                .contains("REPEAT_POLICY_CLAIM");
    }

    @Test
    void claimBelowAlertThresholdCreatesNoAlert() {
        var result = service.screen(Fixtures.claim("QUIET-1").build());

        assertThat(result.alertRaised()).isFalse();
        assertThat(alerts.existsByClaimId("QUIET-1")).isFalse();
    }
}
