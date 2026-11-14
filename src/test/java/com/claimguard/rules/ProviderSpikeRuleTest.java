package com.claimguard.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimguard.Fixtures;
import com.claimguard.screening.InMemoryClaimHistoryStore;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProviderSpikeRuleTest {

    private static final Instant NOW = Instant.parse("2026-06-30T12:00:00Z");
    private static final String PROVIDER = "GAR-77";

    private InMemoryClaimHistoryStore store;
    private ProviderSpikeRule rule;

    @BeforeEach
    void setUp() {
        store = new InMemoryClaimHistoryStore();
        rule = new ProviderSpikeRule(Fixtures.props(), store);
    }

    private void record(String claimId, Instant reportedAt) {
        store.record(Fixtures.claim(claimId).provider(PROVIDER).reportedAt(reportedAt).build());
    }

    private boolean firesFor(String claimId) {
        var claim = Fixtures.claim(claimId).provider(PROVIDER).reportedAt(NOW).build();
        return rule.evaluate(claim).isPresent();
    }

    @Test
    void tenClaimsInTotalDoNotFireAndTheEleventhFires() {
        for (int i = 0; i < 9; i++) {
            record("H" + i, NOW.minus(Duration.ofMinutes(10 + i)));
        }
        assertThat(firesFor("C-10")).isFalse();      // 9 earlier + this claim = 10 total
        record("H9", NOW.minus(Duration.ofMinutes(1)));
        assertThat(firesFor("C-11")).isTrue();       // 10 earlier + this claim = 11 total
    }

    @Test
    void aClaimExactlySevenDaysEarlierCounts() {
        for (int i = 0; i < 9; i++) {
            record("H" + i, NOW.minus(Duration.ofMinutes(10 + i)));
        }
        record("EDGE", NOW.minus(Duration.ofDays(7)));       // exactly at the inclusive window start
        assertThat(firesFor("C")).isTrue();                  // 10 earlier + this claim = 11
    }

    @Test
    void aClaimSevenDaysAndOneMilliEarlierDoesNotCount() {
        for (int i = 0; i < 9; i++) {
            record("H" + i, NOW.minus(Duration.ofMinutes(10 + i)));
        }
        record("EDGE", NOW.minus(Duration.ofDays(7)).minusMillis(1));
        assertThat(firesFor("C")).isFalse();                 // only 9 earlier + this claim = 10
    }

    @Test
    void theClaimItselfIsNotDoubleCounted() {
        for (int i = 0; i < 9; i++) {
            record("H" + i, NOW.minus(Duration.ofMinutes(10 + i)));
        }
        record("C", NOW);                                    // this very claim, already in history
        assertThat(firesFor("C")).isFalse();                 // excluded by claimId -> 9 earlier + this = 10
    }
}
