package com.shbean.solana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.rpc.RpcApi;
import org.p2p.solanaj.rpc.types.config.Commitment;

import com.shbean.solana.SolanaClient.AccountMeta;
import com.shbean.solana.SolanaClient.Instruction;

class MicroloanProgramTest {

    private static final String MICROLOAN = "7RPbrV9Ft664iLK95bYsZbNascy9wwX57Tk6UCAM4uXk";
    private static final String MINT = "Cmg8i52JnPxSXQ67a8pYaWBgJtUoMek5oz6bgb5DuNM3";
    private static final String BANK = "9rKgpQgeCbDhMr6P7k1XgZnUM75DxM2rB6iXvggH5goQ";
    private static final String POOL = "E4Vw2myrgtZBeUx4QVEBuoYj9kmVDr4jaEJLpHKvcmem";
    private static final String VAULT = "13UD7PwE7B1vZSgPnw5AYpoydgfPWSsNUam6ozgBV4qJ";
    private static final UUID REF = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e0f");
    private static final String LOAN = "EDNJQFxcV3jLEJ6t4SRa4jsDvXt3uxAunP67f8rZqkxC";

    private final SolanaClient client = new SolanajClient(mock(RpcApi.class), Commitment.CONFIRMED);
    private final MicroloanProgram program = new MicroloanProgram(client, MICROLOAN, MINT);

    @Test
    void derivesThePinnedPoolVaultAndLoanAddresses() {
        assertThat(program.pool()).isEqualTo(POOL);
        assertThat(program.vault()).isEqualTo(VAULT);
        assertThat(program.loanAddress(REF)).isEqualTo(LOAN);
    }

    @Test
    void associatedTokenAddressMatchesSplTokenCli() {
        // `spl-token address --token <mint> --owner <bank> --verbose`
        assertThat(SplToken.associatedTokenAddress(client, BANK, MINT)).isEqualTo("8C2v4erUDDNaKQp1BbaVgKAkd3m2adHvYXhjJfcu1maD");
    }

    @Test
    void createLoanFollowsTheIdlAccountOrder() {
        Instruction ix = program.createLoan(BANK, "Borrower", "BorrowerToken", REF, 200_000_000L, 10_000_000L,
                202611, 202701);

        assertThat(ix.programId()).isEqualTo(MICROLOAN);
        assertThat(ix.accounts()).containsExactly(
                AccountMeta.signerWritable(BANK),
                AccountMeta.signerReadonly("Borrower"),
                AccountMeta.writable(POOL),
                AccountMeta.readonly(MINT),
                AccountMeta.writable("BorrowerToken"),
                AccountMeta.writable(LOAN),
                AccountMeta.readonly(SplToken.TOKEN_PROGRAM),
                AccountMeta.readonly(SplToken.SYSTEM_PROGRAM));
        assertThat(ix.data()).isEqualTo(AnchorCodec.createLoan(REF, 200_000_000L, 10_000_000L, 202611, 202701));
    }

    @Test
    void disburseAndRepayMoveFundsBetweenVaultAndBorrower() {
        List<AccountMeta> expected = List.of(
                AccountMeta.signerReadonly(BANK),
                AccountMeta.writable(POOL),
                AccountMeta.writable(LOAN),
                AccountMeta.readonly(MINT),
                AccountMeta.writable(VAULT),
                AccountMeta.writable("BorrowerToken"),
                AccountMeta.readonly(SplToken.TOKEN_PROGRAM));

        Instruction disburse = program.disburse(BANK, REF, "BorrowerToken");
        Instruction repay = program.repay(BANK, REF, "BorrowerToken");

        assertThat(disburse.accounts()).isEqualTo(expected);
        assertThat(repay.accounts()).isEqualTo(expected);
        assertThat(disburse.data()).isEqualTo(AnchorCodec.instructionDiscriminator("disburse"));
        assertThat(repay.data()).isEqualTo(AnchorCodec.instructionDiscriminator("repay"));
    }

    @Test
    void poolInstructions() {
        assertThat(program.initPool(BANK).accounts()).containsExactly(
                AccountMeta.signerWritable(BANK), AccountMeta.readonly(MINT), AccountMeta.writable(POOL),
                AccountMeta.writable(VAULT), AccountMeta.readonly(SplToken.TOKEN_PROGRAM),
                AccountMeta.readonly(SplToken.SYSTEM_PROGRAM));

        Instruction fund = program.fundPool(BANK, "BankToken", 5_000_000L);
        assertThat(fund.accounts()).containsExactly(
                AccountMeta.signerReadonly(BANK), AccountMeta.readonly(POOL), AccountMeta.readonly(MINT),
                AccountMeta.writable(VAULT), AccountMeta.writable("BankToken"), AccountMeta.readonly(SplToken.TOKEN_PROGRAM));
        assertThat(Arrays.copyOf(fund.data(), 8)).isEqualTo(AnchorCodec.instructionDiscriminator("fund_pool"));
        assertThat(ByteBuffer.wrap(fund.data(), 8, 8).order(ByteOrder.LITTLE_ENDIAN).getLong()).isEqualTo(5_000_000L);

        assertThat(program.markDefault(BANK, REF).accounts()).containsExactly(
                AccountMeta.signerReadonly(BANK), AccountMeta.writable(POOL), AccountMeta.writable(LOAN));
    }

    @Test
    void splTokenEncodings() {
        Instruction mintTo = SplToken.mintTo(MINT, "Dest", BANK, 1_500_000L);
        assertThat(mintTo.programId()).isEqualTo(SplToken.TOKEN_PROGRAM);
        assertThat(mintTo.data()).isEqualTo(new byte[] {7, (byte) 0x60, (byte) 0xE3, 0x16, 0, 0, 0, 0, 0});
        assertThat(mintTo.accounts()).containsExactly(
                AccountMeta.writable(MINT), AccountMeta.writable("Dest"), AccountMeta.signerReadonly(BANK));

        Instruction ata = SplToken.createAssociatedTokenAccountIdempotent(BANK, "Ata", "Owner", MINT);
        assertThat(ata.programId()).isEqualTo(SplToken.ASSOCIATED_TOKEN_PROGRAM);
        assertThat(ata.data()).isEqualTo(new byte[] {1});

        byte[] account = new byte[165];
        ByteBuffer.wrap(account, 64, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(42_000_000L);
        assertThat(SplToken.amount(account)).isEqualTo(42_000_000L);
        assertThatThrownBy(() -> SplToken.amount(new byte[10])).isInstanceOf(IllegalArgumentException.class);
    }
}
