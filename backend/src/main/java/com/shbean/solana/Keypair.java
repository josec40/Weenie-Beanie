package com.shbean.solana;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Ed25519 signing key in Solana's 64-byte layout (32-byte seed followed by the 32-byte public key). */
public final class Keypair {

    private static final int SECRET_LENGTH = 64;
    private static final int KEY_LENGTH = 32;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final byte[] secretKey;

    private Keypair(byte[] secretKey) {
        this.secretKey = secretKey;
    }

    public static Keypair generate() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] seed = tail(pair.getPrivate().getEncoded(), KEY_LENGTH);
            byte[] publicKey = tail(pair.getPublic().getEncoded(), KEY_LENGTH);
            byte[] secret = new byte[SECRET_LENGTH];
            System.arraycopy(seed, 0, secret, 0, KEY_LENGTH);
            System.arraycopy(publicKey, 0, secret, KEY_LENGTH, KEY_LENGTH);
            return new Keypair(secret);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 is not available", e);
        }
    }

    /** The public half is taken from the last 32 bytes as written by {@code solana-keygen}; it is not re-derived. */
    public static Keypair fromSecretKey(byte[] secretKey) {
        if (secretKey == null || secretKey.length != SECRET_LENGTH) {
            throw new IllegalArgumentException("A Solana secret key is exactly " + SECRET_LENGTH + " bytes");
        }
        return new Keypair(Arrays.copyOf(secretKey, SECRET_LENGTH));
    }

    /** Parses the JSON byte array format of {@code solana-keygen} keypair files. */
    public static Keypair fromJson(String json) {
        try {
            int[] values = JSON.readValue(json, int[].class);
            byte[] secret = new byte[values.length];
            for (int i = 0; i < values.length; i++) {
                if (values[i] < 0 || values[i] > 255) {
                    throw new IllegalArgumentException("Keypair JSON must contain bytes");
                }
                secret[i] = (byte) values[i];
            }
            return fromSecretKey(secret);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Keypair JSON must be an array of 64 bytes");
        }
    }

    public String address() {
        return Base58.encode(publicKey());
    }

    public byte[] publicKey() {
        return Arrays.copyOfRange(secretKey, KEY_LENGTH, SECRET_LENGTH);
    }

    /** Defensive copy; callers are responsible for never logging or persisting it unencrypted. */
    public byte[] secretKeyBytes() {
        return Arrays.copyOf(secretKey, SECRET_LENGTH);
    }

    @Override
    public String toString() {
        return "Keypair[" + address() + "]";
    }

    private static byte[] tail(byte[] encoded, int length) {
        return Arrays.copyOfRange(encoded, encoded.length - length, encoded.length);
    }
}
