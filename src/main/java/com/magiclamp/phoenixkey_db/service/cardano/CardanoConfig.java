package com.magiclamp.phoenixkey_db.service.cardano;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cardano integration config — bound từ {@code phoenixkey.cardano.*} trong application.yml.
 *
 * <ul>
 *   <li>{@code network} — preprod | preview | mainnet (mặc định preprod)</li>
 *   <li>{@code blockfrostApiKey} — API key cho Blockfrost backend (chain indexer)</li>
 *   <li>{@code confirmTimeoutMs} — timeout chờ tx confirm trên chain (mặc định 120s)</li>
 * </ul>
 *
 * Fee wallet mnemonic KHÔNG đặt ở đây — load từ Vault qua
 * {@code VaultSecretService.getFeeWalletMnemonic()} ở {@code FeeWalletService.@PostConstruct}.
 */
@ConfigurationProperties(prefix = "phoenixkey.cardano")
public record CardanoConfig(
        String network,
        String blockfrostApiKey,
        long confirmTimeoutMs) {

    public CardanoConfig {
        if (network == null || network.isBlank()) {
            network = "preprod";
        }
        if (confirmTimeoutMs <= 0) {
            confirmTimeoutMs = 120_000;
        }
    }

    /** True nếu mainnet (network ID = 1), false cho preprod/preview (network ID = 0). */
    public boolean isMainnet() {
        return "mainnet".equalsIgnoreCase(network);
    }
}
