package com.shbean.settlement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Makes sure the devnet loan pool exists and holds mock USDC at startup. Best effort: sagas re-check before paying out. */
@Component
@Order(3)
@ConditionalOnProperty(prefix = "solana", name = "enabled", havingValue = "true")
public class BankBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BankBootstrap.class);

    private final SolanaBank bank;

    public BankBootstrap(SolanaBank bank) {
        this.bank = bank;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            bank.ensurePool(1);
            log.info("Solana bank {} ready; pool {} holds {} mock USDC units", bank.authority().address(),
                    bank.microloan().pool(), bank.tokenBalance(bank.microloan().vault()));
        } catch (RuntimeException e) {
            log.warn("Solana pool bootstrap failed (will be retried by the next disbursement): {}", e.getMessage());
        }
    }
}
