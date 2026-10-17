package com.shbean.credential;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shbean.settlement.SolanaBank;
import com.shbean.solana.AnchorCodec.ReputationAccount;
import com.shbean.solana.Base58;

/**
 * Partner verification, read live from the on-chain Reputation PDA (not the database). Keyed by wallet and returns
 * no PII. Also serves as the soulbound credential's metadata URI, hence {@code name}/{@code symbol}/{@code description}.
 */
@RestController
public class PublicCredentialController {

    public record PublicCredential(String name, String symbol, String description, String wallet,
                                   String reputationAccount, String issuerConfig, String program, int tier,
                                   int onTimeRepayments, int lateRepayments, int defaults, BigDecimal totalRepaidUsd,
                                   String credentialMint, Instant updatedAt) {
    }

    static final String NAME = "ShBean Repayment Credential";
    static final String SYMBOL = "SHBREP";

    private final ObjectProvider<ReputationLedger> chain;

    public PublicCredentialController(ObjectProvider<ReputationLedger> chain) {
        this.chain = chain;
    }

    @GetMapping("/api/public/credentials/{wallet}")
    public PublicCredential verify(@PathVariable String wallet) {
        if (!isAddress(wallet)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a Solana address");
        }
        ReputationLedger ledger = chain.getIfAvailable();
        if (ledger == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No credential");
        }
        ReputationAccount rep = ledger.read(wallet)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No credential"));
        return new PublicCredential(NAME, SYMBOL,
                "Non-transferable record of micro-loan repayments issued by ShBean. Tier " + rep.tier() + ".",
                wallet, ledger.program().reputationAddress(wallet), ledger.program().config(), ledger.program().programId(),
                rep.tier(), rep.onTimeRepayments(), rep.lateRepayments(), rep.defaults(),
                BigDecimal.valueOf(rep.totalRepaid()).movePointLeft(SolanaBank.DECIMALS).setScale(2, RoundingMode.DOWN),
                rep.hasCredential() ? Base58.encode(rep.sbtMint()) : null, Instant.ofEpochSecond(rep.updatedAt()));
    }

    private static boolean isAddress(String s) {
        try {
            return s.length() <= 44 && Base58.decode(s).length == 32;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
