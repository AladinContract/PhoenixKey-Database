package com.magiclamp.phoenixkey_db.service.cardano;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean factory cho BloxBean {@code BackendService} (Blockfrost) + {@code Network}.
 *
 * Network ID:
 * <ul>
 *   <li>preprod / preview → 0 (testnet)</li>
 *   <li>mainnet → 1</li>
 * </ul>
 *
 * Blockfrost URL chọn theo {@code phoenixkey.cardano.network}:
 * <ul>
 *   <li>preprod  → {@link Constants#BLOCKFROST_PREPROD_URL}</li>
 *   <li>preview  → {@link Constants#BLOCKFROST_PREVIEW_URL}</li>
 *   <li>mainnet  → {@link Constants#BLOCKFROST_MAINNET_URL}</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(CardanoConfig.class)
public class CardanoBackend {

    private final CardanoConfig config;

    @Autowired
    public CardanoBackend(CardanoConfig config) {
        this.config = config;
    }

    @Bean
    public BackendService backendService() {
        String url = switch (config.network().toLowerCase()) {
            case "mainnet" -> Constants.BLOCKFROST_MAINNET_URL;
            case "preview" -> Constants.BLOCKFROST_PREVIEW_URL;
            default -> Constants.BLOCKFROST_PREPROD_URL;
        };
        if (config.blockfrostApiKey() == null || config.blockfrostApiKey().isBlank()) {
            throw new IllegalStateException(
                    "BLOCKFROST_API_KEY chưa được set — vào https://blockfrost.io tạo project + lấy key");
        }
        return new BFBackendService(url, config.blockfrostApiKey());
    }

    @Bean
    public Network cardanoNetwork() {
        return switch (config.network().toLowerCase()) {
            case "mainnet" -> Networks.mainnet();
            case "preview" -> Networks.preview();
            default -> Networks.preprod();
        };
    }
}
