package com.shbean.solana;

/** Any failure talking to Solana. Messages never include request URLs or key material. */
public class SolanaException extends RuntimeException {

    public SolanaException(String message) {
        super(message);
    }
}
