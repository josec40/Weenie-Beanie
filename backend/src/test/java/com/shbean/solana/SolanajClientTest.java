package com.shbean.solana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.ConfirmedTransaction;
import org.p2p.solanaj.rpc.types.LatestBlockhash;
import org.p2p.solanaj.rpc.types.SignatureStatuses;
import org.p2p.solanaj.rpc.types.config.Commitment;
import org.p2p.solanaj.rpc.types.config.RpcSendTransactionConfig;

import com.shbean.solana.SolanaClient.AccountMeta;
import com.shbean.solana.SolanaClient.Instruction;
import com.shbean.solana.SolanaClient.Pda;
import com.shbean.solana.SolanaClient.SignedTransaction;
import com.shbean.solana.SolanaClient.Status;
import com.shbean.solana.SolanaClient.TxResult;

class SolanajClientTest {

    private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";
    private static final String BLOCKHASH = Base58.encode(filled(7));

    private final RpcApi rpc = mock(RpcApi.class);
    private final SolanajClient client = new SolanajClient(rpc, Commitment.CONFIRMED);

    @BeforeEach
    void stubBlockhash() throws RpcException {
        LatestBlockhash latest = mock(LatestBlockhash.class);
        LatestBlockhash.Value value = mock(LatestBlockhash.Value.class);
        when(value.getBlockhash()).thenReturn(BLOCKHASH);
        when(latest.getValue()).thenReturn(value);
        when(rpc.getLatestBlockhash(Commitment.CONFIRMED)).thenReturn(latest);
    }

    @Test
    void pdaDerivationMatchesTheRustProgram() {
        String microloan = "7RPbrV9Ft664iLK95bYsZbNascy9wwX57Tk6UCAM4uXk";
        String mint = "Cmg8i52JnPxSXQ67a8pYaWBgJtUoMek5oz6bgb5DuNM3";

        Pda pool = client.findProgramAddress(List.of("pool".getBytes(), Base58.decode(mint)), microloan);
        Pda vault = client.findProgramAddress(List.of("vault".getBytes(), Base58.decode(pool.address())), microloan);
        byte[] loanRef = AnchorCodec.uuidBytes(UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e0f"));
        Pda loan = client.findProgramAddress(List.of("loan".getBytes(), Base58.decode(pool.address()), loanRef), microloan);

        // Pinned in solana/programs/microloan/tests/test_microloan.rs (pda_derivation_golden_vectors).
        assertThat(pool).isEqualTo(new Pda("E4Vw2myrgtZBeUx4QVEBuoYj9kmVDr4jaEJLpHKvcmem", 255));
        assertThat(vault).isEqualTo(new Pda("13UD7PwE7B1vZSgPnw5AYpoydgfPWSsNUam6ozgBV4qJ", 255));
        assertThat(loan).isEqualTo(new Pda("EDNJQFxcV3jLEJ6t4SRa4jsDvXt3uxAunP67f8rZqkxC", 255));
    }

    @Test
    void everySignatureOnAMultiSignerTransactionVerifiesAgainstItsAccountKey() throws Exception {
        Keypair bank = Keypair.generate();
        Keypair borrower = Keypair.generate();
        Keypair extra = Keypair.generate();
        String target = Keypair.generate().address();
        // Readonly signer listed before a writable one, to exercise solanaj's account ordering.
        Instruction ix = new Instruction(SYSTEM_PROGRAM, List.of(
                AccountMeta.signerReadonly(borrower.address()),
                AccountMeta.signerWritable(bank.address()),
                AccountMeta.signerWritable(extra.address()),
                AccountMeta.writable(target)), transferData(1_000));

        SignedTransaction tx = client.sign(List.of(ix), bank, List.of(borrower, extra));

        ByteBuffer wire = ByteBuffer.wrap(tx.wire());
        int signatures = wire.get() & 0xFF;
        assertThat(signatures).isEqualTo(3);
        byte[][] sigs = new byte[signatures][64];
        for (byte[] s : sigs) {
            wire.get(s);
        }
        byte[] message = Arrays.copyOfRange(tx.wire(), wire.position(), tx.wire().length);
        ByteBuffer m = ByteBuffer.wrap(message);
        m.position(3);
        int keyCount = m.get() & 0xFF;
        byte[][] keys = new byte[keyCount][32];
        for (byte[] k : keys) {
            m.get(k);
        }
        for (int i = 0; i < signatures; i++) {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(KeypairTest.publicKey(keys[i]));
            verifier.update(message);
            assertThat(verifier.verify(sigs[i])).as("signature %d matches account key %d", i, i).isTrue();
        }
        assertThat(Base58.encode(keys[0])).isEqualTo(bank.address());
        assertThat(tx.signature()).isEqualTo(Base58.encode(sigs[0]));
        assertThat(tx.blockhash()).isEqualTo(BLOCKHASH);
    }

    @Test
    void signersMustMatchWhatTheInstructionsRequire() {
        Keypair bank = Keypair.generate();
        Keypair stranger = Keypair.generate();
        Instruction ix = transfer(bank, Keypair.generate().address());

        assertThatThrownBy(() -> client.sign(List.of(ix), bank, List.of(stranger)))
                .isInstanceOf(SolanaException.class);
        assertThatThrownBy(() -> client.sign(List.of(ix), stranger, List.of()))
                .isInstanceOf(SolanaException.class);
    }

    @Test
    void submitSendsTheExactWireBytesAndRejectsAMismatchedSignature() throws Exception {
        Keypair bank = Keypair.generate();
        SignedTransaction tx = client.sign(List.of(transfer(bank, Keypair.generate().address())), bank, List.of());
        when(rpc.sendRawTransaction(anyString(), any(RpcSendTransactionConfig.class))).thenReturn(tx.signature());

        client.submit(tx);

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RpcSendTransactionConfig> config = ArgumentCaptor.forClass(RpcSendTransactionConfig.class);
        verify(rpc).sendRawTransaction(sent.capture(), config.capture());
        assertThat(Base64.getDecoder().decode(sent.getValue())).isEqualTo(tx.wire());
        assertThat(config.getValue().isSkipPreflight()).isFalse();
        assertThat(config.getValue().getPreflightCommitment()).isEqualTo("confirmed");

        when(rpc.sendRawTransaction(anyString(), any(RpcSendTransactionConfig.class))).thenReturn("someOtherSignature");
        assertThatThrownBy(() -> client.submit(tx)).isInstanceOf(SolanaException.class).hasMessageContaining("unexpected signature");
    }

    @Test
    void rpcFailuresBecomeSolanaExceptionsWithoutLeakingTheCause() throws Exception {
        when(rpc.sendRawTransaction(anyString(), any(RpcSendTransactionConfig.class)))
                .thenThrow(new RpcException("Transaction simulation failed: custom program error: 0x1770"));
        Keypair bank = Keypair.generate();
        SignedTransaction tx = client.sign(List.of(transfer(bank, Keypair.generate().address())), bank, List.of());

        assertThatThrownBy(() -> client.submit(tx))
                .isInstanceOf(SolanaException.class)
                .hasMessage("Solana sendTransaction failed: Transaction simulation failed: custom program error: 0x1770")
                .hasNoCause();
    }

    @Test
    void rateLimitedCallsAreRetriedWithBackoffThenGiveUp() throws Exception {
        SolanajClient patient = new SolanajClient(rpc, Commitment.CONFIRMED, java.time.Duration.ofMillis(1));
        String address = Keypair.generate().address();
        when(rpc.getBalance(any(), eq(Commitment.CONFIRMED)))
                .thenThrow(new RpcException("Too many requests for a specific RPC call"))
                .thenThrow(new RpcException("HTTP 429"))
                .thenReturn(42L);
        assertThat(patient.getBalance(address)).isEqualTo(42L);

        when(rpc.isBlockhashValid(anyString(), any(), any()))
                .thenThrow(new RpcException("Too many requests for a specific RPC call"));
        assertThatThrownBy(() -> patient.isBlockhashValid(BLOCKHASH)).isInstanceOf(SolanaException.class)
                .hasMessageContaining("Too many requests");
        verify(rpc, org.mockito.Mockito.times(6)).isBlockhashValid(anyString(), any(), any());
    }

    @Test
    void statusDistinguishesUnseenPendingConfirmedFinalizedAndFailed() throws Exception {
        assertThat(statusFor(null, null)).isEqualTo(Status.NOT_FOUND);
        assertThat(statusFor("processed", null)).isEqualTo(Status.PROCESSED);
        assertThat(statusFor("confirmed", ok())).isEqualTo(Status.CONFIRMED);
        assertThat(statusFor("finalized", ok())).isEqualTo(Status.FINALIZED);
        assertThat(statusFor("confirmed", null)).as("confirmed but result unreadable").isEqualTo(Status.PROCESSED);

        ConfirmedTransaction.Meta failedMeta = mock(ConfirmedTransaction.Meta.class);
        when(failedMeta.getErr()).thenReturn("InstructionError");
        ConfirmedTransaction failed = mock(ConfirmedTransaction.class);
        when(failed.getMeta()).thenReturn(failedMeta);
        TxResult result = resultFor("confirmed", failed);
        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.error()).isEqualTo("InstructionError");
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void sendAndConfirmPollsUntilConfirmed() throws Exception {
        Keypair bank = Keypair.generate();
        when(rpc.sendRawTransaction(anyString(), any(RpcSendTransactionConfig.class))).thenAnswer(inv -> {
            byte[] wire = Base64.getDecoder().decode((String) inv.getArgument(0));
            return Base58.encode(Arrays.copyOfRange(wire, 1, 65));
        });
        SignatureStatuses pending = statuses("processed");
        SignatureStatuses confirmed = statuses("confirmed");
        ConfirmedTransaction landed = ok();
        when(rpc.getSignatureStatuses(anyList(), anyBoolean())).thenReturn(pending, confirmed);
        when(rpc.getTransaction(anyString(), eq(Commitment.CONFIRMED))).thenReturn(landed);

        TxResult result = client.sendAndConfirm(List.of(transfer(bank, Keypair.generate().address())), bank, List.of(),
                java.time.Duration.ofSeconds(10));

        assertThat(result.status()).isEqualTo(Status.CONFIRMED);
        assertThat(result.succeeded()).isTrue();
    }

    private Status statusFor(String level, ConfirmedTransaction landed) throws Exception {
        return resultFor(level, landed).status();
    }

    private TxResult resultFor(String level, ConfirmedTransaction landed) throws Exception {
        SignatureStatuses statuses = level == null ? emptyStatuses() : statuses(level);
        when(rpc.getSignatureStatuses(anyList(), anyBoolean())).thenReturn(statuses);
        when(rpc.getTransaction(anyString(), eq(Commitment.CONFIRMED))).thenReturn(landed);
        return client.status("sig");
    }

    private static SignatureStatuses statuses(String level) {
        SignatureStatuses.Value value = mock(SignatureStatuses.Value.class);
        when(value.getConfirmationStatus()).thenReturn(level);
        SignatureStatuses statuses = mock(SignatureStatuses.class);
        when(statuses.getValue()).thenReturn(List.of(value));
        return statuses;
    }

    private static SignatureStatuses emptyStatuses() {
        SignatureStatuses statuses = mock(SignatureStatuses.class);
        when(statuses.getValue()).thenReturn(java.util.Collections.singletonList(null));
        return statuses;
    }

    private static ConfirmedTransaction ok() {
        ConfirmedTransaction.Meta meta = mock(ConfirmedTransaction.Meta.class);
        when(meta.getErr()).thenReturn(null);
        ConfirmedTransaction tx = mock(ConfirmedTransaction.class);
        when(tx.getMeta()).thenReturn(meta);
        return tx;
    }

    private static Instruction transfer(Keypair from, String to) {
        return new Instruction(SYSTEM_PROGRAM,
                List.of(AccountMeta.signerWritable(from.address()), AccountMeta.writable(to)), transferData(5_000));
    }

    /** System program Transfer: u32 LE instruction index 2, then u64 LE lamports. */
    private static byte[] transferData(long lamports) {
        return ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(2).putLong(lamports).array();
    }

    private static byte[] filled(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
