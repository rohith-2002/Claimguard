package com.claimguard.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaskingTest {

    @Test
    void masksAllButLastFourDigits() {
        assertThat(Masking.mask("9876543210")).isEqualTo("******3210");
    }

    @Test
    void shortOrMissingValuesAreFullyMasked() {
        assertThat(Masking.mask("123")).isEqualTo("****");
        assertThat(Masking.mask(null)).isEqualTo("****");
    }
}
