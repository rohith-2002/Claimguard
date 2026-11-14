package com.claimguard.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimguard.Fixtures;
import com.claimguard.alerts.LineOfBusiness;
import com.claimguard.screening.InMemoryClaimHistoryStore;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepeatVehicleRuleTest {

    private static final Instant NOW = Instant.parse("2026-06-30T12:00:00Z");
    private static final String VEHICLE = "KA01AB9999";

    private InMemoryClaimHistoryStore store;
    private RepeatVehicleRule rule;

    @BeforeEach
    void setUp() {
        store = new InMemoryClaimHistoryStore();
        rule = new RepeatVehicleRule(Fixtures.props(), store);
    }

    private void earlierClaimOnOtherPolicy(Instant reportedAt) {
        store.record(Fixtures.claim("EARLIER").policy("POL-OTHER").vehicle(VEHICLE).reportedAt(reportedAt).build());
    }

    private boolean firesForCurrentMotorClaim() {
        var claim = Fixtures.claim("CUR").policy("POL-CUR").vehicle(VEHICLE).reportedAt(NOW).build();
        return rule.evaluate(claim).isPresent();
    }

    @Test
    void eightyNineDaysEarlierOnAnotherPolicyFires() {
        earlierClaimOnOtherPolicy(NOW.minus(Duration.ofDays(89)));
        assertThat(firesForCurrentMotorClaim()).isTrue();
    }

    @Test
    void exactlyNinetyDaysEarlierFires() {
        earlierClaimOnOtherPolicy(NOW.minus(Duration.ofDays(90)));
        assertThat(firesForCurrentMotorClaim()).isTrue();
    }

    @Test
    void ninetyDaysAndOneSecondEarlierDoesNotFire() {
        earlierClaimOnOtherPolicy(NOW.minus(Duration.ofDays(90)).minusSeconds(1));
        assertThat(firesForCurrentMotorClaim()).isFalse();
    }

    @Test
    void aClaimExactlyAtReportedAtDoesNotFire() {
        earlierClaimOnOtherPolicy(NOW);
        assertThat(firesForCurrentMotorClaim()).isFalse();
    }

    @Test
    void theSamePolicyIsIgnored() {
        store.record(Fixtures.claim("EARLIER").policy("POL-CUR").vehicle(VEHICLE)
                .reportedAt(NOW.minus(Duration.ofDays(10))).build());
        assertThat(firesForCurrentMotorClaim()).isFalse();
    }

    @Test
    void aNullVehicleDoesNotFire() {
        earlierClaimOnOtherPolicy(NOW.minus(Duration.ofDays(10)));
        var health = Fixtures.claim("CUR").policy("POL-CUR").lob(LineOfBusiness.HEALTH).reportedAt(NOW).build();
        assertThat(rule.evaluate(health)).isEmpty();
    }
}
