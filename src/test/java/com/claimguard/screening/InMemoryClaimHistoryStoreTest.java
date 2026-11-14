package com.claimguard.screening;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimguard.Fixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryClaimHistoryStoreTest {

    private final InMemoryClaimHistoryStore store = new InMemoryClaimHistoryStore();

    @Test
    void recordReturnsTrueTheFirstTimeAndFalseForTheSameClaimId() {
        var claim = Fixtures.claim("C1").reportedAt(Instant.parse("2026-06-30T12:00:00Z")).build();
        assertThat(store.record(claim)).isTrue();
        assertThat(store.record(claim)).isFalse();
    }
}
