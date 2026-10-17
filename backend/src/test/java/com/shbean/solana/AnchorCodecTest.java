package com.shbean.solana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shbean.solana.AnchorCodec.LoanAccount;
import com.shbean.solana.AnchorCodec.LoanStatus;

/** Golden vectors produced by the Rust program (solana/programs/microloan/tests/test_microloan.rs) and its IDL. */
class AnchorCodecTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final UUID REF = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d0e0f");

    @Test
    void discriminatorsMatchTheAnchorIdl() {
        assertThat(AnchorCodec.instructionDiscriminator("create_loan")).containsExactly(bytes(166, 131, 118, 219, 138, 218, 206, 140));
        assertThat(AnchorCodec.instructionDiscriminator("repay")).containsExactly(bytes(234, 103, 67, 82, 208, 234, 219, 166));
        assertThat(AnchorCodec.instructionDiscriminator("disburse")).containsExactly(bytes(68, 250, 205, 89, 217, 142, 13, 44));
        assertThat(AnchorCodec.accountDiscriminator("Loan")).containsExactly(bytes(20, 195, 70, 117, 165, 227, 182, 1));
        assertThat(AnchorCodec.accountDiscriminator("Pool")).containsExactly(bytes(241, 154, 109, 4, 17, 177, 109, 188));
    }

    @Test
    void createLoanDataMatchesRustGoldenVector() {
        byte[] data = AnchorCodec.createLoan(REF, 342_000_000L, 17_100_000L, 202702, 202704);
        assertThat(HEX.formatHex(data))
                .isEqualTo("a68376db8adace8c000102030405060708090a0b0c0d0e0f8081621400000000e0ec040100000000ce170300d0170300");
    }

    @Test
    void decodesLoanAccountLayout() {
        ByteBuffer b = ByteBuffer.allocate(8 + 32 * 3 + 16 + 8 + 8 + 4 + 4 + 1 + 8 * 3 + 1).order(ByteOrder.LITTLE_ENDIAN);
        b.put(AnchorCodec.accountDiscriminator("Loan"));
        b.put(filled(1)).put(filled(2)).put(filled(3));
        b.put(AnchorCodec.uuidBytes(REF));
        b.putLong(342_000_000L).putLong(17_100_000L).putInt(202702).putInt(202704);
        b.put((byte) 2).putLong(100L).putLong(200L).putLong(300L).put((byte) 254);

        LoanAccount loan = AnchorCodec.decodeLoan(b.array());

        assertThat(loan.borrower()).isEqualTo(filled(2));
        assertThat(loan.loanRef()).isEqualTo(REF);
        assertThat(loan.principal()).isEqualTo(342_000_000L);
        assertThat(loan.status()).isEqualTo(LoanStatus.REPAID);
        assertThat(loan.settledAt()).isEqualTo(300L);
        assertThatThrownBy(() -> AnchorCodec.decodeLoan(new byte[200])).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static byte[] filled(int value) {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) value);
        return k;
    }
}
