package com.shbean.solana;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Thin, library-independent view of Solana for the settlement flow. Addresses and signatures are base58 strings.
 * The only implementation today is {@link SolanajClient}; nothing outside it may depend on solanaj types.
 *
 * <p>Sending is split into {@link #sign} and {@link #submit} because the signature is known before submission: the
 * caller can persist it first and, after a crash, ask {@link #status} whether the transaction landed instead of
 * sending a second one.
 */
public interface SolanaClient {

    record AccountMeta(String address, boolean signer, boolean writable) {
        public static AccountMeta signerWritable(String address) {
            return new AccountMeta(address, true, true);
        }

        public static AccountMeta signerReadonly(String address) {
            return new AccountMeta(address, true, false);
        }

        public static AccountMeta writable(String address) {
            return new AccountMeta(address, false, true);
        }

        public static AccountMeta readonly(String address) {
            return new AccountMeta(address, false, false);
        }
    }

    record Instruction(String programId, List<AccountMeta> accounts, byte[] data) {
    }

    record AccountData(String owner, long lamports, boolean executable, byte[] data) {
    }

    record Pda(String address, int bump) {
    }

    /** {@code wire} is the exact serialized transaction; {@code blockhash} bounds how long it can still land. */
    record SignedTransaction(String signature, String blockhash, byte[] wire) {
    }

    enum Status {
        /** Not seen by the cluster (never sent, dropped, or expired). */
        NOT_FOUND,
        /** Seen but not yet confirmed, or confirmed without a readable result. */
        PROCESSED,
        CONFIRMED,
        FINALIZED,
        /** Landed on-chain but the program returned an error; {@code error} holds the cluster's description. */
        FAILED
    }

    record TxResult(String signature, Status status, String error) {
        public boolean succeeded() {
            return status == Status.CONFIRMED || status == Status.FINALIZED;
        }
    }

    /** Empty if the account does not exist. */
    Optional<AccountData> getAccount(String address);

    long getBalance(String address);

    Pda findProgramAddress(List<byte[]> seeds, String programId);

    /** Fetches a recent blockhash and signs; {@code feePayer} signs first, {@code otherSigners} may be empty. */
    SignedTransaction sign(List<Instruction> instructions, Keypair feePayer, List<Keypair> otherSigners);

    /** Broadcasts with preflight simulation; a rejected transaction throws and never lands. */
    void submit(SignedTransaction transaction);

    TxResult status(String signature);

    /** False once the blockhash has expired, i.e. an unseen transaction can no longer land. */
    boolean isBlockhashValid(String blockhash);

    default TxResult awaitConfirmation(String signature, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        TxResult result = status(signature);
        while (!result.succeeded() && result.status() != Status.FAILED && System.nanoTime() < deadline) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SolanaException("Interrupted while waiting for " + signature);
            }
            result = status(signature);
        }
        return result;
    }

    default TxResult sendAndConfirm(List<Instruction> instructions, Keypair feePayer, List<Keypair> otherSigners,
                                    Duration timeout) {
        SignedTransaction tx = sign(instructions, feePayer, otherSigners);
        submit(tx);
        return awaitConfirmation(tx.signature(), timeout);
    }
}
