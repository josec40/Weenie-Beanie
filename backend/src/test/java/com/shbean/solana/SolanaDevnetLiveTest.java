package com.shbean.solana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.shbean.solana.SolanaClient.AccountMeta;
import com.shbean.solana.SolanaClient.Instruction;
import com.shbean.solana.SolanaClient.SignedTransaction;
import com.shbean.solana.SolanaClient.Status;
import com.shbean.solana.SolanaClient.TxResult;

/**
 * Talks to the real devnet and spends a few thousand lamports. Opt in with {@code SHBEAN_SOLANA_LIVE=true};
 * the paying keypair file comes from {@code SHBEAN_SOLANA_KEYPAIR} (default: the bank keypair).
 */
@EnabledIfEnvironmentVariable(named = "SHBEAN_SOLANA_LIVE", matches = "true")
class SolanaDevnetLiveTest {

    private static final String SYSTEM_PROGRAM = "11111111111111111111111111111111";
    private static final String MICROLOAN = "7RPbrV9Ft664iLK95bYsZbNascy9wwX57Tk6UCAM4uXk";
    private static final String DEPLOYER = "Ds9FBXFtcqnSxTXFwpMdktn9Z3MGMfWJHy9j51d3dPs1";

    private static SolanaClient client;
    private static Keypair bank;

    @BeforeAll
    static void connect() throws Exception {
        client = new SolanajClient(new SolanaProperties("https://api.devnet.solana.com", "confirmed", Duration.ofSeconds(45)));
        String path = System.getenv().getOrDefault("SHBEAN_SOLANA_KEYPAIR",
                System.getProperty("user.home") + "/.config/solana/shbean-bank.json");
        bank = Keypair.fromJson(Files.readString(Path.of(path)));
    }

    @Test
    void readsTheDeployedProgramAndReportsMissingAccounts() {
        var program = client.getAccount(MICROLOAN).orElseThrow();
        assertThat(program.executable()).isTrue();
        assertThat(program.owner()).isEqualTo("BPFLoaderUpgradeab1e11111111111111111111111");

        assertThat(client.getAccount(Keypair.generate().address())).isEmpty();
        assertThat(client.getBalance(bank.address())).isPositive();
    }

    @Test
    void multiSignerTransferConfirmsAndMovesLamports() {
        Keypair witness = Keypair.generate();
        long before = client.getBalance(DEPLOYER);
        // The witness is a readonly signer the system program ignores; it exercises multi-signer ordering on-chain.
        Instruction transfer = new Instruction(SYSTEM_PROGRAM, List.of(
                AccountMeta.signerWritable(bank.address()),
                AccountMeta.writable(DEPLOYER),
                AccountMeta.signerReadonly(witness.address())), transferData(1_000));

        SignedTransaction tx = client.sign(List.of(transfer), bank, List.of(witness));
        assertThat(client.status(tx.signature()).status()).isEqualTo(Status.NOT_FOUND);
        assertThat(client.isBlockhashValid(tx.blockhash())).isTrue();
        client.submit(tx);
        TxResult result = client.awaitConfirmation(tx.signature(), Duration.ofSeconds(45));

        assertThat(result.succeeded()).as(String.valueOf(result)).isTrue();
        assertThat(client.getBalance(DEPLOYER)).isGreaterThanOrEqualTo(before + 1_000);
    }

    @Test
    void doomedTransactionIsRejectedByPreflightAndNeverLands() {
        long tooMuch = client.getBalance(bank.address()) + 1_000_000_000L;
        Instruction transfer = new Instruction(SYSTEM_PROGRAM,
                List.of(AccountMeta.signerWritable(bank.address()), AccountMeta.writable(DEPLOYER)), transferData(tooMuch));
        SignedTransaction tx = client.sign(List.of(transfer), bank, List.of());

        assertThatThrownBy(() -> client.submit(tx)).isInstanceOf(SolanaException.class);
        assertThat(client.status(tx.signature()).status()).isEqualTo(Status.NOT_FOUND);
    }

    private static byte[] transferData(long lamports) {
        return ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(2).putLong(lamports).array();
    }
}
