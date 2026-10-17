package com.shbean.settlement;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.shbean.loan.Loan;
import com.shbean.settlement.OnChainExecutor.Plan;
import com.shbean.solana.AnchorCodec;
import com.shbean.solana.AnchorCodec.LoanAccount;
import com.shbean.solana.AnchorCodec.LoanStatus;
import com.shbean.solana.Keypair;
import com.shbean.solana.MicroloanProgram;
import com.shbean.solana.SolanaClient;
import com.shbean.solana.SolanaClient.Instruction;
import com.shbean.solana.SplToken;
import com.shbean.wallet.WalletService;

/**
 * On-chain half of settlement (microloan escrow). Each method is one saga step, safe to call again after a crash:
 * the executor resolves recorded signatures and the {@code alreadyDone} checks read the loan PDA / token balances.
 */
@Component
@ConditionalOnProperty(prefix = "solana", name = "enabled", havingValue = "true")
public class LoanLedger {

    static final String CREATE_LOAN = "CREATE_LOAN";
    static final String DISBURSE = "DISBURSE";
    static final String PAY_VENDOR = "PAY_VENDOR";
    static final String MARK_DEFAULT = "MARK_DEFAULT";

    private final SolanaClient client;
    private final SolanaBank bank;
    private final MicroloanProgram microloan;
    private final WalletService wallets;
    private final OnChainExecutor executor;

    public LoanLedger(SolanaClient client, SolanaBank bank, WalletService wallets, OnChainExecutor executor) {
        this.client = client;
        this.bank = bank;
        this.microloan = bank.microloan();
        this.wallets = wallets;
        this.executor = executor;
    }

    static String onRampStep(int attempt) {
        return "ONRAMP#" + attempt;
    }

    static String repayStep(int attempt) {
        return "REPAY#" + attempt;
    }

    /** Creates the borrower's token account (if needed) and the loan PDA; the borrower signs the auto-repay approve. */
    public String open(Loan loan) {
        Keypair borrower = wallets.keypairFor(loan.getUserId());
        String token = SplToken.associatedTokenAddress(client, borrower.address(), microloan.mint());
        String authority = bank.authority().address();
        int disburse = yyyymm(loan.getDisburseMonth());
        // The program requires repay > disburse; reschedules only ever move the repayment later.
        int repay = Math.max(yyyymm(loan.getRepayMonth()), yyyymm(loan.getDisburseMonth().plusMonths(1)));
        return executor.execute(loan.getId(), CREATE_LOAN, () -> onChain(loan).isPresent(), () -> new Plan(List.of(
                SplToken.createAssociatedTokenAccountIdempotent(authority, token, borrower.address(), microloan.mint()),
                microloan.createLoan(authority, borrower.address(), token, loan.getId(),
                        SolanaBank.units(loan.getPrincipal()), SolanaBank.units(loan.getFee()), disburse, repay)),
                bank.authority(), List.of(borrower)));
    }

    /** Pool vault → borrower token account. */
    public String disburse(Loan loan) {
        return executor.execute(loan.getId(), DISBURSE,
                () -> onChain(loan).map(a -> a.status() != LoanStatus.CREATED).orElse(false),
                () -> {
                    bank.ensurePool(SolanaBank.units(loan.getPrincipal()));
                    return bankPlan(microloan.disburse(bank.authority().address(), loan.getId(), borrowerToken(loan)));
                });
    }

    /**
     * Transfers the full purchase amount from the borrower's token account to the vendor's token account,
     * minting the borrower's own portion if needed so the balance covers it.
     */
    public String payVendor(Loan loan) {
        if (loan.getPurchaseAmount() == null) {
            return null;
        }
        long fullAmount = SolanaBank.units(loan.getPurchaseAmount());
        String borrowerToken = borrowerToken(loan);
        String vendorWallet = bank.vendorWalletAddress();
        String vendorToken = SplToken.associatedTokenAddress(client, vendorWallet, microloan.mint());
        Keypair borrower = wallets.keypairFor(loan.getUserId());
        String authority = bank.authority().address();

        return executor.execute(loan.getId(), PAY_VENDOR,
                () -> loan.getSolanaVendorSignature() != null,
                () -> {
                    List<Instruction> instructions = new ArrayList<>();
                    instructions.add(SplToken.createAssociatedTokenAccountIdempotent(authority, vendorToken, vendorWallet, microloan.mint()));
                    long currentBal = bank.tokenBalance(borrowerToken);
                    if (currentBal < fullAmount) {
                        instructions.add(SplToken.mintTo(microloan.mint(), borrowerToken, authority, fullAmount - currentBal));
                    }
                    instructions.add(SplToken.transfer(borrowerToken, vendorToken, borrower.address(), fullAmount));
                    return new Plan(instructions, bank.authority(), List.of(borrower));
                });
    }

    /**
     * Mirrors the fiat withdrawal on-chain: mints mock USDC so the borrower's token account covers principal + fee
     * (it still holds the disbursed principal, so normally only the fee is minted).
     */
    public void onRamp(Loan loan) {
        long due = SolanaBank.units(loan.getTotalDue());
        String token = borrowerToken(loan);
        executor.execute(loan.getId(), onRampStep(loan.getRepayAttempt()),
                () -> isRepaid(loan) || bank.tokenBalance(token) >= due,
                () -> bankPlan(SplToken.mintTo(microloan.mint(), token, bank.authority().address(),
                        due - bank.tokenBalance(token))));
    }

    /** The loan PDA (SPL delegate) pulls principal + fee into the vault; the bank cranks it. */
    public String repay(Loan loan) {
        return executor.execute(loan.getId(), repayStep(loan.getRepayAttempt()), () -> isRepaid(loan),
                () -> bankPlan(microloan.repay(bank.authority().address(), loan.getId(), borrowerToken(loan))));
    }

    public void awaitRepay(Loan loan) {
        executor.awaitPending(loan.getId(), repayStep(loan.getRepayAttempt()));
    }

    public boolean isRepaid(Loan loan) {
        return onChain(loan).map(a -> a.status() == LoanStatus.REPAID).orElse(false);
    }

    /** Empty if the loan never reached the chain; otherwise marks a disbursed loan defaulted. */
    public Optional<String> markDefault(Loan loan) {
        if (onChain(loan).isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(executor.execute(loan.getId(), MARK_DEFAULT,
                () -> onChain(loan).map(a -> a.status() != LoanStatus.DISBURSED).orElse(true),
                () -> bankPlan(microloan.markDefault(bank.authority().address(), loan.getId()))));
    }

    private Optional<LoanAccount> onChain(Loan loan) {
        return client.getAccount(microloan.loanAddress(loan.getId())).map(a -> AnchorCodec.decodeLoan(a.data()));
    }

    private String borrowerToken(Loan loan) {
        String owner = wallets.address(loan.getUserId())
                .orElseThrow(() -> new SettlementException("No wallet for the borrower of loan " + loan.getId()));
        return SplToken.associatedTokenAddress(client, owner, microloan.mint());
    }

    private Plan bankPlan(Instruction instruction) {
        return new Plan(List.of(instruction), bank.authority(), List.of());
    }

    private static int yyyymm(YearMonth month) {
        return month.getYear() * 100 + month.getMonthValue();
    }
}
