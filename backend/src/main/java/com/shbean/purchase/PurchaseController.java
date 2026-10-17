package com.shbean.purchase;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import com.shbean.loan.Loan;
import com.shbean.loan.LoanService;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

@Controller
public class PurchaseController {

    private final PurchaseService purchases;
    private final LoanService loans;
    private final AppUserRepository users;

    public PurchaseController(PurchaseService purchases, LoanService loans, AppUserRepository users) {
        this.purchases = purchases;
        this.loans = loans;
        this.users = users;
    }

    @QueryMapping
    public PurchaseCheckResult purchaseCheck(@Argument BigDecimal amount, @Argument String label,
                                             @AuthenticationPrincipal Jwt jwt) {
        return purchases.purchaseCheck(user(jwt), amount, label);
    }

    @MutationMapping
    public Loan acceptPurchaseLoan(@Argument BigDecimal amount, @Argument String label,
                                   @Argument boolean consent, @Argument BigDecimal expectedTotalDue,
                                   @Argument String merchantId, @AuthenticationPrincipal Jwt jwt) {
        return loans.acceptPurchase(user(jwt), amount, label, consent, expectedTotalDue, merchantId);
    }

    @MutationMapping
    public boolean payMerchant(@Argument String merchantId, @Argument BigDecimal amount,
                                @Argument String description, @AuthenticationPrincipal Jwt jwt) {
        return purchases.payMerchant(user(jwt), merchantId, amount, description);
    }

    private AppUser user(Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
