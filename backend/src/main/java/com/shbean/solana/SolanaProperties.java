package com.shbean.solana;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled             when false, settlement is fiat-only (Nessie) and no on-chain beans are created
 * @param rpcUrl              JSON-RPC endpoint; hosted providers put the API key in the URL, so it is never logged
 * @param commitment          commitment for reads, blockhash and preflight: processed, confirmed or finalized
 * @param confirmTimeout      how long a submitted transaction is awaited before the step is retried later
 * @param cluster             explorer cluster name for transaction links (devnet, testnet, mainnet-beta)
 * @param microloanProgramId  deployed {@code microloan} program
 * @param reputationProgramId deployed {@code reputation} program
 * @param usdcMint            mock USDC mint; the bank keypair must be its mint authority
 * @param bankKeypairPath     {@code solana-keygen} JSON file of the bank authority (pays fees and rent)
 * @param walletMasterKey     base64 AES-256 key encrypting custodial wallet secrets ({@code WALLET_MASTER_KEY})
 * @param poolTopUpUsdc       whole mock USDC minted into the pool vault whenever it cannot cover a disbursement
 */
@ConfigurationProperties("solana")
public record SolanaProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://api.devnet.solana.com") String rpcUrl,
        @DefaultValue("confirmed") String commitment,
        @DefaultValue("PT45S") Duration confirmTimeout,
        @DefaultValue("devnet") String cluster,
        String microloanProgramId,
        String reputationProgramId,
        String usdcMint,
        String bankKeypairPath,
        String walletMasterKey,
        @DefaultValue("10000") long poolTopUpUsdc) {

    /** Transport-only settings, e.g. for tests that talk to an RPC node without settlement. */
    public static SolanaProperties rpcOnly(String rpcUrl, String commitment, Duration confirmTimeout) {
        return new SolanaProperties(false, rpcUrl, commitment, confirmTimeout, "devnet", null, null, null, null, null, 0);
    }

    public String explorerTxUrl(String signature) {
        return explorerUrl("tx/" + signature);
    }

    public String explorerAddressUrl(String address) {
        return explorerUrl("address/" + address);
    }

    private String explorerUrl(String path) {
        String base = "https://explorer.solana.com/" + path;
        return "mainnet-beta".equals(cluster) ? base : base + "?cluster=" + cluster;
    }

    @Override
    public String toString() {
        return "SolanaProperties[enabled=" + enabled + ", rpcUrl=***, commitment=" + commitment + ", confirmTimeout="
                + confirmTimeout + ", cluster=" + cluster + ", microloanProgramId=" + microloanProgramId
                + ", reputationProgramId=" + reputationProgramId + ", usdcMint=" + usdcMint + ", walletMasterKey=***]";
    }
}
