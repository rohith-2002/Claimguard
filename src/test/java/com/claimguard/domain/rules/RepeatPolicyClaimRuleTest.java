package com.claimguard.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimguard.Fixtures;
import com.claimguard.domain.TriggeredRule;
import com.claimguard.store.InMemoryClaimHistoryStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepeatPolicyClaimRuleTest {

    private static final Instant NOW = Instant.parse("2026-06-30T12:00:00Z");
    private static final Duration WINDOW = Duration.ofDays(180);

    private InMemoryClaimHistoryStore store;
    private RepeatPolicyClaimRule rule;

    @BeforeEach
    void setUp() {
        store = new InMemoryClaimHistoryStore();
        rule = new RepeatPolicyClaimRule(Fixtures.props(), store);
    }

    private void earlierClaim(String claimId, Instant reportedAt) {
        store.record(Fixtures.claim(claimId).policy("POL-X").reportedAt(reportedAt).build());
    }

    private Optional<TriggeredRule> evaluateCurrent() {
        return rule.evaluate(Fixtures.claim("CUR").policy("POL-X").reportedAt(NOW).build());
    }

    @Test
    void oneEarlierClaimDoesNotFire() {
        earlierClaim("A", NOW.minus(Duration.ofDays(10)));
        assertThat(evaluateCurrent()).isEmpty();
    }

    @Test
    void twoEarlierClaimsFireAtExactThreshold() {
        earlierClaim("A", NOW.minus(Duration.ofDays(10)));
        earlierClaim("B", NOW.minus(Duration.ofDays(5)));
        TriggeredRule hit = evaluateCurrent().orElseThrow();
        assertThat(hit.code()).isEqualTo("REPEAT_POLICY_CLAIM");
        assertThat(hit.weight()).isEqualTo(25);
        assertThat(hit.reason()).isEqualTo("2 earlier claims on policy POL-X in the last 180 days (limit 2)");
    }

    @Test
    void claimExactly180DaysEarlierIsInsideTheWindow() {
        earlierClaim("A", NOW.minus(WINDOW));
        earlierClaim("B", NOW.minus(Duration.ofDays(5)));
        assertThat(evaluateCurrent()).isPresent();
    }

    @Test
    void claimJustOver180DaysEarlierIsOutsideTheWindow() {
        earlierClaim("A", NOW.minus(WINDOW).minusSeconds(1));
        earlierClaim("B", NOW.minus(Duration.ofDays(5)));
        assertThat(evaluateCurrent()).isEmpty();
    }

    @Test
    void claimWithSameReportedAtIsNotEarlier() {
        earlierClaim("A", NOW.minus(Duration.ofDays(5)));
        earlierClaim("B", NOW);
        assertThat(evaluateCurrent()).isEmpty();
    }

    @Test
    void sameClaimIdRecordedTwiceCountsOnce() {
        earlierClaim("A", NOW.minus(Duration.ofDays(10)));
        earlierClaim("A", NOW.minus(Duration.ofDays(10)));
        earlierClaim("B", NOW.minus(Duration.ofDays(5)));
        earlierClaim("B", NOW.minus(Duration.ofDays(5)));
        assertThat(evaluateCurrent().orElseThrow().reason()).startsWith("2 earlier claims");
    }

    @Test
    void ignoresTheClaimsOwnId() {
        earlierClaim("CUR", NOW.minus(Duration.ofDays(1)));
        earlierClaim("A", NOW.minus(Duration.ofDays(10)));
        assertThat(evaluateCurrent()).isEmpty();
    }

    @Test
    void otherPoliciesAreIgnored() {
        store.record(Fixtures.claim("A").policy("POL-OTHER").reportedAt(NOW.minus(Duration.ofDays(10))).build());
        store.record(Fixtures.claim("B").policy("POL-OTHER").reportedAt(NOW.minus(Duration.ofDays(5))).build());
        assertThat(evaluateCurrent()).isEmpty();
    }
}
