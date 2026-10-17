package com.shbean.credential;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import com.shbean.credential.CredentialStore.Credential;
import com.shbean.loan.LoanProperties;
import com.shbean.solana.SolanaProperties;

@Controller
public class CredentialController {

    public record CredentialView(boolean onChain, int tier, BigDecimal cap, Integer nextTierAt, int effectiveOnTime,
                                 int onTimeRepayments, int lateRepayments, int defaults, BigDecimal totalRepaid,
                                 String walletAddress, String walletUrl, String credentialMint, String credentialUrl,
                                 String credentialTxUrl, String verifyUrl) {
    }

    private final CredentialService credentials;
    private final LoanProperties loans;
    private final SolanaProperties solana;
    private final ObjectProvider<ReputationLedger> chain;

    public CredentialController(CredentialService credentials, LoanProperties loans, SolanaProperties solana,
                                ObjectProvider<ReputationLedger> chain) {
        this.credentials = credentials;
        this.loans = loans;
        this.solana = solana;
        this.chain = chain;
    }

    @QueryMapping
    public CredentialView credential(@AuthenticationPrincipal Jwt jwt) {
        return view(UUID.fromString(jwt.getSubject()));
    }

    public CredentialView view(UUID userId) {
        Credential c = credentials.current(userId);
        ReputationLedger ledger = chain.getIfAvailable();
        String wallet = c.wallet();
        return new CredentialView(ledger != null && wallet != null, c.tier(), loans.capFor(c.tier()),
                Tiers.nextTierAt(c.tier()), Tiers.effectiveOnTime(c.onTimeRepayments(), c.defaults()),
                c.onTimeRepayments(), c.lateRepayments(), c.defaults(), c.totalRepaid(), wallet,
                wallet == null ? null : solana.explorerAddressUrl(wallet), c.sbtMint(),
                c.sbtMint() == null ? null : solana.explorerAddressUrl(c.sbtMint()),
                c.sbtMintSignature() == null ? null : solana.explorerTxUrl(c.sbtMintSignature()),
                ledger == null || wallet == null ? null : ledger.verifyUrl(wallet));
    }
}
