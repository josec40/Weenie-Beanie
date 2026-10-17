package com.shbean.solana;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import com.shbean.solana.SolanaClient.AccountMeta;
import com.shbean.solana.SolanaClient.Instruction;

/** The few SPL Token / Associated Token Account instructions settlement needs (classic Token program). */
public final class SplToken {

    public static final String TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
    public static final String ASSOCIATED_TOKEN_PROGRAM = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL";
    public static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";

    private static final byte MINT_TO = 7;
    private static final byte CREATE_IDEMPOTENT = 1;
    private static final int AMOUNT_OFFSET = 64;

    private SplToken() {
    }

    public static String associatedTokenAddress(SolanaClient client, String owner, String mint) {
        return client.findProgramAddress(
                List.of(Base58.decode(owner), Base58.decode(TOKEN_PROGRAM), Base58.decode(mint)),
                ASSOCIATED_TOKEN_PROGRAM).address();
    }

    /** Creates {@code ata} for {@code owner} unless it already exists; {@code payer} funds the rent. */
    public static Instruction createAssociatedTokenAccountIdempotent(String payer, String ata, String owner, String mint) {
        return new Instruction(ASSOCIATED_TOKEN_PROGRAM, List.of(
                AccountMeta.signerWritable(payer),
                AccountMeta.writable(ata),
                AccountMeta.readonly(owner),
                AccountMeta.readonly(mint),
                AccountMeta.readonly(SYSTEM_PROGRAM),
                AccountMeta.readonly(TOKEN_PROGRAM)), new byte[] {CREATE_IDEMPOTENT});
    }

    public static Instruction mintTo(String mint, String destination, String mintAuthority, long amount) {
        byte[] data = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).put(MINT_TO).putLong(amount).array();
        return new Instruction(TOKEN_PROGRAM, List.of(
                AccountMeta.writable(mint),
                AccountMeta.writable(destination),
                AccountMeta.signerReadonly(mintAuthority)), data);
    }

    /** Raw token amount of an SPL token account (mint 32 + owner 32, then u64 LE). */
    public static long amount(byte[] tokenAccountData) {
        if (tokenAccountData == null || tokenAccountData.length < AMOUNT_OFFSET + 8) {
            throw new IllegalArgumentException("Not an SPL token account");
        }
        return ByteBuffer.wrap(tokenAccountData, AMOUNT_OFFSET, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }
}
