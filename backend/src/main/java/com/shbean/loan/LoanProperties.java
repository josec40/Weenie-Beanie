package com.shbean.loan;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param feeRate               flat fee as a fraction of principal (0.05 = 5%)
 * @param tierCaps              max principal per credential tier (index = tier)
 * @param minBufferAfterRepay   auto-repay only if the balance stays at least this high afterwards
 * @param graceMonths           months a LATE loan can retry before DEFAULTED
 * @param repaymentWindowMonths a repayment month must fall within shortfall month + N
 */
@ConfigurationProperties("app.loans")
public record LoanProperties(
        @DefaultValue("0.05") BigDecimal feeRate,
        @DefaultValue({"200", "500", "1000"}) List<BigDecimal> tierCaps,
        @DefaultValue("25") BigDecimal minBufferAfterRepay,
        @DefaultValue("1") int graceMonths,
        @DefaultValue("3") int repaymentWindowMonths) {

    public BigDecimal capFor(int tier) {
        return tierCaps.get(Math.clamp(tier, 0, tierCaps.size() - 1));
    }
}
