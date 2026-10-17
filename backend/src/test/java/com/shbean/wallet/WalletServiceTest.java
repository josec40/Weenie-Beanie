package com.shbean.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shbean.solana.Keypair;
import com.shbean.solana.SolanaProperties;

class WalletServiceTest {

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static WalletService service(String masterKey) {
        SolanaProperties props = new SolanaProperties(true, "http://localhost", "confirmed", null, "devnet", null, null,
                null, null, masterKey, 0);
        return new WalletService(null, props);
    }

    @Test
    void secretRoundTripsAndIsBoundToItsUser() {
        WalletService wallets = service(randomKey());
        UUID alice = UUID.randomUUID();
        byte[] secret = Keypair.generate().secretKeyBytes();

        byte[] sealed = wallets.encrypt(alice, secret);
        assertThat(sealed).hasSize(12 + 64 + 16);
        assertThat(wallets.decrypt(alice, sealed)).isEqualTo(secret);
        // Same plaintext encrypts differently each time (fresh IV).
        assertThat(wallets.encrypt(alice, secret)).isNotEqualTo(sealed);

        assertThatThrownBy(() -> wallets.decrypt(UUID.randomUUID(), sealed))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be decrypted");
        sealed[20] ^= 1;
        assertThatThrownBy(() -> wallets.decrypt(alice, sealed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherMasterKeyCannotDecrypt() {
        UUID user = UUID.randomUUID();
        byte[] sealed = service(randomKey()).encrypt(user, new byte[64]);

        assertThatThrownBy(() -> service(randomKey()).decrypt(user, sealed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void masterKeyMustBe32Base64Bytes() {
        assertThatThrownBy(() -> service(null)).hasMessageContaining("WALLET_MASTER_KEY");
        assertThatThrownBy(() -> service("not base64!")).hasMessageContaining("base64");
        assertThatThrownBy(() -> service(Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("32 random bytes");
    }
}
