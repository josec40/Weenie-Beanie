package com.shbean.solana;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.shbean.solana.SolanaClient.AccountMeta;
import com.shbean.solana.SolanaClient.Instruction;

/** Instruction builders for the {@code microloan} program; account order and flags mirror the Anchor IDL. */
public final class MicroloanProgram {

    private final SolanaClient client;
    private final String programId;
    private final String mint;
    private final String pool;
    private final String vault;

    public MicroloanProgram(SolanaClient client, String programId, String mint) {
        this.client = client;
        this.programId = programId;
        this.mint = mint;
        this.pool = pda(seed("pool"), Base58.decode(mint));
        this.vault = pda(seed("vault"), Base58.decode(pool));
    }

    public String programId() {
        return programId;
    }

    public String mint() {
        return mint;
    }

    public String pool() {
        return pool;
    }

    public String vault() {
        return vault;
    }

    public String loanAddress(UUID loanRef) {
        return pda(seed("loan"), Base58.decode(pool), AnchorCodec.uuidBytes(loanRef));
    }

    public Instruction initPool(String authority) {
        return new Instruction(programId, List.of(
                AccountMeta.signerWritable(authority),
                AccountMeta.readonly(mint),
                AccountMeta.writable(pool),
                AccountMeta.writable(vault),
                AccountMeta.readonly(SplToken.TOKEN_PROGRAM),
                AccountMeta.readonly(SplToken.SYSTEM_PROGRAM)), AnchorCodec.noArgs("init_pool"));
    }

    public Instruction fundPool(String authority, String authorityToken, long amount) {
        return new Instruction(programId, List.of(
                AccountMeta.signerReadonly(authority),
                AccountMeta.readonly(pool),
                AccountMeta.readonly(mint),
                AccountMeta.writable(vault),
                AccountMeta.writable(authorityToken),
                AccountMeta.readonly(SplToken.TOKEN_PROGRAM)), AnchorCodec.fundPool(amount));
    }

    public Instruction createLoan(String authority, String borrower, String borrowerToken, UUID loanRef, long principal,
                                  long fee, int disburseMonth, int repayMonth) {
        return new Instruction(programId, List.of(
                AccountMeta.signerWritable(authority),
                AccountMeta.signerReadonly(borrower),
                AccountMeta.writable(pool),
                AccountMeta.readonly(mint),
                AccountMeta.writable(borrowerToken),
                AccountMeta.writable(loanAddress(loanRef)),
                AccountMeta.readonly(SplToken.TOKEN_PROGRAM),
                AccountMeta.readonly(SplToken.SYSTEM_PROGRAM)),
                AnchorCodec.createLoan(loanRef, principal, fee, disburseMonth, repayMonth));
    }

    public Instruction disburse(String authority, UUID loanRef, String borrowerToken) {
        return moveFunds("disburse", authority, loanRef, borrowerToken);
    }

    /** The loan PDA is the borrower's SPL delegate, so the borrower does not sign. */
    public Instruction repay(String authority, UUID loanRef, String borrowerToken) {
        return moveFunds("repay", authority, loanRef, borrowerToken);
    }

    public Instruction markDefault(String authority, UUID loanRef) {
        return new Instruction(programId, List.of(
                AccountMeta.signerReadonly(authority),
                AccountMeta.writable(pool),
                AccountMeta.writable(loanAddress(loanRef))), AnchorCodec.noArgs("mark_default"));
    }

    private Instruction moveFunds(String name, String authority, UUID loanRef, String borrowerToken) {
        return new Instruction(programId, List.of(
                AccountMeta.signerReadonly(authority),
                AccountMeta.writable(pool),
                AccountMeta.writable(loanAddress(loanRef)),
                AccountMeta.readonly(mint),
                AccountMeta.writable(vault),
                AccountMeta.writable(borrowerToken),
                AccountMeta.readonly(SplToken.TOKEN_PROGRAM)), AnchorCodec.noArgs(name));
    }

    private String pda(byte[]... seeds) {
        return client.findProgramAddress(List.of(seeds), programId).address();
    }

    private static byte[] seed(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
