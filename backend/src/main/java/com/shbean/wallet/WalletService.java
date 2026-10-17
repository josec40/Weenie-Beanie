package com.shbean.wallet;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.shbean.solana.Keypair;
import com.shbean.solana.SolanaProperties;

/**
 * Custodial devnet wallets, one per user (decision 4). Secrets are stored as {@code iv || AES-256-GCM(secret)} with
 * the user id as associated data, so a ciphertext copied to another user's row fails to decrypt.
 */
@Service
@ConditionalOnProperty(prefix = "solana", name = "enabled", havingValue = "true")
public class WalletService {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final JdbcTemplate jdbc;
    private final SecretKey masterKey;
    private final SecureRandom random = new SecureRandom();

    public WalletService(JdbcTemplate jdbc, SolanaProperties props) {
        this.jdbc = jdbc;
        this.masterKey = masterKey(props.walletMasterKey());
    }

    /** The user's wallet, created on first use. Safe under concurrent calls for the same user. */
    public Keypair keypairFor(UUID userId) {
        Optional<Keypair> existing = load(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Keypair fresh = Keypair.generate();
        byte[] secret = fresh.secretKeyBytes();
        try {
            jdbc.update("INSERT INTO wallet (user_id, public_key, encrypted_secret) VALUES (?, ?, ?) "
                    + "ON CONFLICT (user_id) DO NOTHING", userId, fresh.address(), encrypt(userId, secret));
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
        return load(userId).orElseThrow();
    }

    public Optional<String> address(UUID userId) {
        return jdbc.queryForList("SELECT public_key FROM wallet WHERE user_id = ?", String.class, userId)
                .stream().findFirst();
    }

    private Optional<Keypair> load(UUID userId) {
        List<Keypair> rows = jdbc.query("SELECT public_key, encrypted_secret FROM wallet WHERE user_id = ?", (rs, i) -> {
            byte[] secret = decrypt(userId, rs.getBytes("encrypted_secret"));
            try {
                Keypair k = Keypair.fromSecretKey(secret);
                if (!k.address().equals(rs.getString("public_key"))) {
                    throw new IllegalStateException("Wallet record is inconsistent for user " + userId);
                }
                return k;
            } finally {
                Arrays.fill(secret, (byte) 0);
            }
        }, userId);
        return rows.stream().findFirst();
    }

    byte[] encrypt(UUID userId, byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance(CIPHER);
            c.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(aad(userId));
            byte[] sealed = c.doFinal(plaintext);
            return ByteBuffer.allocate(IV_LENGTH + sealed.length).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Wallet encryption failed");
        }
    }

    byte[] decrypt(UUID userId, byte[] stored) {
        try {
            Cipher c = Cipher.getInstance(CIPHER);
            c.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, stored, 0, IV_LENGTH));
            c.updateAAD(aad(userId));
            return c.doFinal(stored, IV_LENGTH, stored.length - IV_LENGTH);
        } catch (GeneralSecurityException e) {
            // Wrong master key or tampered row; never include key material in the message.
            throw new IllegalStateException("Wallet secret for user " + userId + " cannot be decrypted");
        }
    }

    private static byte[] aad(UUID userId) {
        return ByteBuffer.allocate(16).putLong(userId.getMostSignificantBits()).putLong(userId.getLeastSignificantBits())
                .array();
    }

    private static SecretKey masterKey(String base64) {
        byte[] key;
        try {
            key = base64 == null ? new byte[0] : Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("WALLET_MASTER_KEY must be base64");
        }
        if (key.length != 32) {
            throw new IllegalStateException("WALLET_MASTER_KEY must be 32 random bytes, base64-encoded "
                    + "(e.g. `openssl rand -base64 32`)");
        }
        return new SecretKeySpec(key, "AES");
    }
}
