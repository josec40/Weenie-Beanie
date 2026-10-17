package com.shbean.solana;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

/**
 * Minimal Anchor wire codec (8-byte discriminators + Borsh little-endian) for the microloan and reputation
 * programs. Library-independent so it works with whichever Java RPC client Phase 7 adopts.
 */
public final class AnchorCodec {

    public enum LoanStatus { CREATED, DISBURSED, REPAID, DEFAULTED }

    /** On-chain {@code microloan::Loan}; pubkeys are raw 32-byte keys. */
    public record LoanAccount(byte[] pool, byte[] borrower, byte[] borrowerToken, UUID loanRef, long principal,
                              long fee, int disburseMonth, int repayMonth, LoanStatus status, long createdAt,
                              long disbursedAt, long settledAt) {
    }

    /** On-chain {@code reputation::Reputation}; {@code sbtMint} is all zeros until the credential is minted. */
    public record ReputationAccount(byte[] config, byte[] wallet, int onTimeRepayments, int lateRepayments,
                                    int defaults, long totalRepaid, int tier, byte[] sbtMint, long updatedAt) {

        public boolean hasCredential() {
            return !Arrays.equals(sbtMint, new byte[32]);
        }
    }

    private AnchorCodec() {
    }

    /** sha256("global:" + name)[0..8], Anchor's instruction discriminator. */
    public static byte[] instructionDiscriminator(String snakeCaseName) {
        return Arrays.copyOf(sha256("global:" + snakeCaseName), 8);
    }

    /** sha256("account:" + Name)[0..8], Anchor's account discriminator. */
    public static byte[] accountDiscriminator(String pascalCaseName) {
        return Arrays.copyOf(sha256("account:" + pascalCaseName), 8);
    }

    public static byte[] createLoan(UUID loanRef, long principal, long fee, int disburseMonth, int repayMonth) {
        ByteBuffer args = le(16 + 8 + 8 + 4 + 4)
                .put(uuidBytes(loanRef))
                .putLong(principal)
                .putLong(fee)
                .putInt(disburseMonth)
                .putInt(repayMonth);
        return concat(instructionDiscriminator("create_loan"), args.array());
    }

    public static byte[] noArgs(String snakeCaseName) {
        return instructionDiscriminator(snakeCaseName);
    }

    public static byte[] fundPool(long amount) {
        return concat(instructionDiscriminator("fund_pool"), le(8).putLong(amount).array());
    }

    public static byte[] recordRepayment(boolean onTime, long amount) {
        return concat(instructionDiscriminator("record_repayment"), le(1 + 8).put((byte) (onTime ? 1 : 0)).putLong(amount).array());
    }

    public static byte[] mintCredential(String uri) {
        byte[] utf8 = uri.getBytes(StandardCharsets.UTF_8);
        return concat(instructionDiscriminator("mint_credential"), le(4 + utf8.length).putInt(utf8.length).put(utf8).array());
    }

    public static LoanAccount decodeLoan(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte[] disc = new byte[8];
        b.get(disc);
        if (!Arrays.equals(disc, accountDiscriminator("Loan"))) {
            throw new IllegalArgumentException("Not a microloan Loan account");
        }
        byte[] pool = key(b);
        byte[] borrower = key(b);
        byte[] borrowerToken = key(b);
        byte[] ref = new byte[16];
        b.get(ref);
        long principal = b.getLong();
        long fee = b.getLong();
        int disburseMonth = b.getInt();
        int repayMonth = b.getInt();
        LoanStatus status = LoanStatus.values()[b.get()];
        return new LoanAccount(pool, borrower, borrowerToken, uuidFrom(ref), principal, fee, disburseMonth, repayMonth,
                status, b.getLong(), b.getLong(), b.getLong());
    }

    public static ReputationAccount decodeReputation(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte[] disc = new byte[8];
        b.get(disc);
        if (!Arrays.equals(disc, accountDiscriminator("Reputation"))) {
            throw new IllegalArgumentException("Not a reputation Reputation account");
        }
        byte[] config = key(b);
        byte[] wallet = key(b);
        int onTime = b.getInt();
        int late = b.getInt();
        int defaults = b.getInt();
        long totalRepaid = b.getLong();
        int tier = b.get() & 0xFF;
        byte[] sbtMint = key(b);
        return new ReputationAccount(config, wallet, onTime, late, defaults, totalRepaid, tier, sbtMint, b.getLong());
    }

    /** UUID as its 16 big-endian bytes (the same order as {@code UUID.toString()} hex). */
    public static byte[] uuidBytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    private static UUID uuidFrom(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes);
        return new UUID(b.getLong(), b.getLong());
    }

    private static byte[] key(ByteBuffer b) {
        byte[] k = new byte[32];
        b.get(k);
        return k;
    }

    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(a.length + b.length);
        out.writeBytes(a);
        out.writeBytes(b);
        return out.toByteArray();
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
