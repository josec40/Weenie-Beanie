package com.shbean.purchase;

import java.math.BigDecimal;
import java.util.List;

import com.shbean.ai.ExplanationService.Explanation;
import com.shbean.forecast.ForecastEngine.Month;

public record PurchaseCheckResult(
        PurchaseVerdict verdict,
        String reason,
        BigDecimal balanceNow,
        BigDecimal gapToday,
        PurchaseProposal proposal,
        String affordableFrom,
        String laterShortfallMonth,
        List<Month> months,
        Explanation explanation
) {
}
