package com.shbean.credential;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Same cases as reputation::tier_for in solana/programs/reputation. */
class TiersTest {

    @Test
    void tierFollowsEffectiveOnTimeRepayments() {
        assertThat(Tiers.tierFor(0, 0)).isZero();
        assertThat(Tiers.tierFor(1, 0)).isZero();
        assertThat(Tiers.tierFor(2, 0)).isEqualTo(1);
        assertThat(Tiers.tierFor(4, 0)).isEqualTo(1);
        assertThat(Tiers.tierFor(5, 0)).isEqualTo(2);
        assertThat(Tiers.tierFor(5, 1)).isEqualTo(1);
        assertThat(Tiers.tierFor(3, 1)).isZero();
        assertThat(Tiers.tierFor(1, 3)).isZero();
    }

    @Test
    void nextTierThresholds() {
        assertThat(Tiers.nextTierAt(0)).isEqualTo(2);
        assertThat(Tiers.nextTierAt(1)).isEqualTo(5);
        assertThat(Tiers.nextTierAt(2)).isNull();
        assertThat(Tiers.effectiveOnTime(1, 3)).isZero();
    }
}
