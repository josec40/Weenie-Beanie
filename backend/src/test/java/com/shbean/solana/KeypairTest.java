package com.shbean.solana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.Account;

class KeypairTest {

    private static final byte[] X509_ED25519_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");
    private static final byte[] PKCS8_ED25519_PREFIX = HexFormat.of().parseHex("302e020100300506032b657004220420");

    @Test
    void base58RoundTripsAndMatchesKnownAddresses() {
        assertThat(Base58.encode(new byte[32])).isEqualTo("11111111111111111111111111111111");
        assertThat(Base58.decode("11111111111111111111111111111111")).isEqualTo(new byte[32]);
        assertThat(Base58.encode(new byte[0])).isEmpty();

        byte[] sample = HexFormat.of().parseHex("00000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e");
        assertThat(Base58.decode(Base58.encode(sample))).isEqualTo(sample);
        assertThat(Base58.encode("hello".getBytes())).isEqualTo("Cn8eVZg");

        assertThatThrownBy(() -> Base58.decode("0OIl")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedKeypairIsAValidSolanaKeypairThatSolanajAgreesWith() {
        Keypair keypair = Keypair.generate();

        assertThat(keypair.secretKeyBytes()).hasSize(64);
        assertThat(keypair.publicKey()).hasSize(32).isEqualTo(Arrays.copyOfRange(keypair.secretKeyBytes(), 32, 64));
        Account account = new Account(keypair.secretKeyBytes());
        assertThat(account.getPublicKeyBase58()).isEqualTo(keypair.address());
        assertThat(Keypair.generate().address()).isNotEqualTo(keypair.address());
    }

    @Test
    void signaturesFromTheSeedVerifyAgainstTheStoredPublicKey() throws Exception {
        Keypair keypair = Keypair.generate();
        byte[] message = "shbean".getBytes();

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey(Arrays.copyOf(keypair.secretKeyBytes(), 32)));
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey(keypair.publicKey()));
        verifier.update(message);
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    void parsesSolanaKeygenJsonAndNeverPrintsTheSecret() {
        Keypair original = Keypair.generate();
        byte[] secret = original.secretKeyBytes();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < secret.length; i++) {
            json.append(i == 0 ? "" : ",").append(secret[i] & 0xFF);
        }

        Keypair parsed = Keypair.fromJson(json.append("]").toString());

        assertThat(parsed.address()).isEqualTo(original.address());
        assertThat(parsed.toString()).isEqualTo("Keypair[" + original.address() + "]");
        assertThat(parsed.toString()).doesNotContain(String.valueOf(secret[0] & 0xFF) + ",");
        assertThatThrownBy(() -> Keypair.fromJson("[1,2,3]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Keypair.fromJson("not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Keypair.fromSecretKey(new byte[63])).isInstanceOf(IllegalArgumentException.class);
    }

    private static PrivateKey privateKey(byte[] seed) throws Exception {
        byte[] encoded = new byte[PKCS8_ED25519_PREFIX.length + seed.length];
        System.arraycopy(PKCS8_ED25519_PREFIX, 0, encoded, 0, PKCS8_ED25519_PREFIX.length);
        System.arraycopy(seed, 0, encoded, PKCS8_ED25519_PREFIX.length, seed.length);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    static PublicKey publicKey(byte[] raw) throws Exception {
        byte[] encoded = new byte[X509_ED25519_PREFIX.length + raw.length];
        System.arraycopy(X509_ED25519_PREFIX, 0, encoded, 0, X509_ED25519_PREFIX.length);
        System.arraycopy(raw, 0, encoded, X509_ED25519_PREFIX.length, raw.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    }
}
