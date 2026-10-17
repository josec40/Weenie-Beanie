package com.shbean.solana;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param rpcUrl         JSON-RPC endpoint; hosted providers put the API key in the URL, so it is never logged
 * @param commitment     commitment for reads, blockhash and preflight: processed, confirmed or finalized
 * @param confirmTimeout how long {@code sendAndConfirm} waits for confirmation
 */
@ConfigurationProperties("solana")
public record SolanaProperties(
        @DefaultValue("https://api.devnet.solana.com") String rpcUrl,
        @DefaultValue("confirmed") String commitment,
        @DefaultValue("PT45S") Duration confirmTimeout) {

    @Override
    public String toString() {
        return "SolanaProperties[rpcUrl=***, commitment=" + commitment + ", confirmTimeout=" + confirmTimeout + "]";
    }
}
