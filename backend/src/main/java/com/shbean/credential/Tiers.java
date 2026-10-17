package com.shbean.credential;

/** Java mirror of {@code reputation::tier_for}; keep both in sync (pinned by TiersTest). */
public final class Tiers {

    public static final int MAX_TIER = 2;

    private Tiers() {
    }

    /** Each default cancels two on-time repayments; late repayments do not count. */
    public static int tierFor(int onTime, int defaults) {
        int effective = effectiveOnTime(onTime, defaults);
        return effective >= 5 ? 2 : effective >= 2 ? 1 : 0;
    }

    public static int effectiveOnTime(int onTime, int defaults) {
        return Math.max(0, onTime - 2 * defaults);
    }

    /** Effective on-time repayments needed for the next tier, or null at the top tier. */
    public static Integer nextTierAt(int tier) {
        return switch (tier) {
            case 0 -> 2;
            case 1 -> 5;
            default -> null;
        };
    }
}
