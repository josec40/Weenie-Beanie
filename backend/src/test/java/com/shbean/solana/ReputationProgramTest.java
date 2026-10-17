package com.shbean.solana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.types.config.Commitment;

import com.shbean.solana.AnchorCodec.ReputationAccount;
import com.shbean.solana.SolanaClient.AccountMeta;
import com.shbean.solana.SolanaClient.Instruction;

class ReputationProgramTest {

    private static final String REPUTATION = "HicA6FJxaS4DTqZL5reqxHmYcXyEKBsPkJnguMUPVnsh";
    private static final String BANK = "9rKgpQgeCbDhMr6P7k1XgZnUM75DxM2rB6iXvggH5goQ";
    private static final String WALLET = Keypair.generate().address();

    private final SolanaClient client = new SolanajClient(mock(RpcApi.class), Commitment.CONFIRMED);
    private final ReputationProgram program = new ReputationProgram(client, REPUTATION, BANK);

    @Test
    void pdasUseTheProgramSeeds() {
        String config = pda("config".getBytes(), Base58.decode(BANK));
        String reputation = pda("reputation".getBytes(), Base58.decode(config), Base58.decode(WALLET));

        assertThat(program.config()).isEqualTo(config);
        assertThat(program.reputationAddress(WALLET)).isEqualTo(reputation);
        assertThat(program.sbtMint(WALLET)).isEqualTo(pda("sbt".getBytes(), Base58.decode(reputation)));
    }

    @Test
    void writeInstructionsFollowTheIdl() {
        String config = program.config();
        String reputation = program.reputationAddress(WALLET);

        assertThat(program.initConfig().accounts()).containsExactly(
                AccountMeta.signerWritable(BANK), AccountMeta.writable(config), AccountMeta.readonly(SplToken.SYSTEM_PROGRAM));
        assertThat(program.initReputation(WALLET).accounts()).containsExactly(
                AccountMeta.signerWritable(BANK), AccountMeta.writable(config), AccountMeta.readonly(WALLET),
                AccountMeta.writable(reputation), AccountMeta.readonly(SplToken.SYSTEM_PROGRAM));

        Instruction record = program.recordRepayment(WALLET, true, 210_000_000L);
        assertThat(record.accounts()).containsExactly(
                AccountMeta.signerReadonly(BANK), AccountMeta.readonly(config), AccountMeta.writable(reputation));
        assertThat(record.data()).isEqualTo(AnchorCodec.recordRepayment(true, 210_000_000L));
        assertThat(program.recordDefault(WALLET).data()).isEqualTo(AnchorCodec.instructionDiscriminator("record_default"));
    }

    @Test
    void mintCredentialUsesToken2022AndTheWalletsAta() {
        String mint = program.sbtMint(WALLET);
        String ata = client.findProgramAddress(List.of(Base58.decode(WALLET), Base58.decode(SplToken.TOKEN_2022_PROGRAM),
                Base58.decode(mint)), SplToken.ASSOCIATED_TOKEN_PROGRAM).address();

        Instruction ix = program.mintCredential(WALLET, "https://example.test/api/public/credentials/" + WALLET);

        assertThat(ix.accounts()).containsExactly(
                AccountMeta.signerWritable(BANK), AccountMeta.readonly(program.config()),
                AccountMeta.writable(program.reputationAddress(WALLET)), AccountMeta.readonly(WALLET),
                AccountMeta.writable(mint), AccountMeta.writable(ata), AccountMeta.readonly(SplToken.TOKEN_2022_PROGRAM),
                AccountMeta.readonly(SplToken.ASSOCIATED_TOKEN_PROGRAM), AccountMeta.readonly(SplToken.SYSTEM_PROGRAM));
        assertThat(ix.data()).isEqualTo(AnchorCodec.mintCredential("https://example.test/api/public/credentials/" + WALLET));
    }

    @Test
    void computeUnitLimitEncoding() {
        Instruction ix = ReputationProgram.computeUnitLimit(400_000);
        assertThat(ix.programId()).isEqualTo("ComputeBudget111111111111111111111111111111");
        assertThat(ix.accounts()).isEmpty();
        assertThat(ix.data()).containsExactly(2, 0x80, 0x1A, 0x06, 0);
    }

    @Test
    void decodesReputationAccounts() {
        byte[] config = Base58.decode(program.config());
        byte[] wallet = Base58.decode(WALLET);
        byte[] mint = Base58.decode(program.sbtMint(WALLET));
        ByteBuffer b = ByteBuffer.allocate(8 + 32 + 32 + 4 + 4 + 4 + 8 + 1 + 32 + 8 + 1).order(ByteOrder.LITTLE_ENDIAN)
                .put(AnchorCodec.accountDiscriminator("Reputation")).put(config).put(wallet)
                .putInt(3).putInt(1).putInt(0).putLong(510_000_000L).put((byte) 1).put(mint).putLong(1_800_000_000L)
                .put((byte) 254);

        ReputationAccount rep = AnchorCodec.decodeReputation(b.array());

        assertThat(rep.onTimeRepayments()).isEqualTo(3);
        assertThat(rep.lateRepayments()).isEqualTo(1);
        assertThat(rep.defaults()).isZero();
        assertThat(rep.totalRepaid()).isEqualTo(510_000_000L);
        assertThat(rep.tier()).isEqualTo(1);
        assertThat(rep.hasCredential()).isTrue();
        assertThat(Base58.encode(rep.wallet())).isEqualTo(WALLET);
        assertThat(rep.updatedAt()).isEqualTo(1_800_000_000L);

        b.position(8 + 32 + 32 + 4 + 4 + 4 + 8 + 1).put(new byte[32]);
        assertThat(AnchorCodec.decodeReputation(b.array()).hasCredential()).isFalse();
    }

    private String pda(byte[]... seeds) {
        return client.findProgramAddress(List.of(seeds), REPUTATION).address();
    }
}
