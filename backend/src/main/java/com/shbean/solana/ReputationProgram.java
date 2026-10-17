package com.shbean.solana;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.shbean.solana.SolanaClient.AccountMeta;
import com.shbean.solana.SolanaClient.Instruction;

/** Instruction builders for the {@code reputation} program; account order and flags mirror the Anchor IDL. */
public final class ReputationProgram {

    public static final String COMPUTE_BUDGET_PROGRAM = "ComputeBudget111111111111111111111111111111";

    private final SolanaClient client;
    private final String programId;
    private final String authority;
    private final String config;

    public ReputationProgram(SolanaClient client, String programId, String authority) {
        this.client = client;
        this.programId = programId;
        this.authority = authority;
        this.config = pda(seed("config"), Base58.decode(authority));
    }

    public String programId() {
        return programId;
    }

    public String config() {
        return config;
    }

    public String reputationAddress(String wallet) {
        return pda(seed("reputation"), Base58.decode(config), Base58.decode(wallet));
    }

    public String sbtMint(String wallet) {
        return pda(seed("sbt"), Base58.decode(reputationAddress(wallet)));
    }

    public Instruction initConfig() {
        return new Instruction(programId, List.of(
                AccountMeta.signerWritable(authority),
                AccountMeta.writable(config),
                AccountMeta.readonly(SplToken.SYSTEM_PROGRAM)), AnchorCodec.noArgs("init_config"));
    }

    public Instruction initReputation(String wallet) {
        return new Instruction(programId, List.of(
                AccountMeta.signerWritable(authority),
                AccountMeta.writable(config),
                AccountMeta.readonly(wallet),
                AccountMeta.writable(reputationAddress(wallet)),
                AccountMeta.readonly(SplToken.SYSTEM_PROGRAM)), AnchorCodec.noArgs("init_reputation"));
    }

    public Instruction recordRepayment(String wallet, boolean onTime, long amount) {
        return update(wallet, AnchorCodec.recordRepayment(onTime, amount));
    }

    public Instruction recordDefault(String wallet) {
        return update(wallet, AnchorCodec.noArgs("record_default"));
    }

    public Instruction mintCredential(String wallet, String uri) {
        String mint = sbtMint(wallet);
        return new Instruction(programId, List.of(
                AccountMeta.signerWritable(authority),
                AccountMeta.readonly(config),
                AccountMeta.writable(reputationAddress(wallet)),
                AccountMeta.readonly(wallet),
                AccountMeta.writable(mint),
                AccountMeta.writable(SplToken.associatedTokenAddress(client, wallet, mint, SplToken.TOKEN_2022_PROGRAM)),
                AccountMeta.readonly(SplToken.TOKEN_2022_PROGRAM),
                AccountMeta.readonly(SplToken.ASSOCIATED_TOKEN_PROGRAM),
                AccountMeta.readonly(SplToken.SYSTEM_PROGRAM)), AnchorCodec.mintCredential(uri));
    }

    /** ComputeBudget {@code SetComputeUnitLimit}; the credential mint (Token-2022 extensions + metadata) is CU-heavy. */
    public static Instruction computeUnitLimit(int units) {
        byte[] data = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).put((byte) 2).putInt(units).array();
        return new Instruction(COMPUTE_BUDGET_PROGRAM, List.of(), data);
    }

    private Instruction update(String wallet, byte[] data) {
        return new Instruction(programId, List.of(
                AccountMeta.signerReadonly(authority),
                AccountMeta.readonly(config),
                AccountMeta.writable(reputationAddress(wallet))), data);
    }

    private String pda(byte[]... seeds) {
        return client.findProgramAddress(List.of(seeds), programId).address();
    }

    private static byte[] seed(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
