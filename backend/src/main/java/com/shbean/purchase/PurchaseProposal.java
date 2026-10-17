package com.shbean.purchase;

import java.math.BigDecimal;

public record PurchaseProposal(BigDecimal principal, BigDecimal fee, String repaymentMonth, BigDecimal totalDue) {
}
