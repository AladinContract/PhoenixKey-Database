package com.magiclamp.phoenixkey_db.service.cardano;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Lightweight Blockfrost HTTP client used by activation + wallet + MAGIC claim
 * flows. Sits alongside the BloxBean-based {@link CardanoService} which handles
 * DID Document tx building/resolving. Keeping these decoupled minimizes
 * tx-builder dep surface for routine HTTP calls.
 */
@Service
@Slf4j
public class BlockfrostHttpClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${phoenixkey.cardano.blockfrost-api-key}")
    private String blockfrostKey;

    @Value("${phoenixkey.cardano.network:preprod}")
    private String network;

    @Value("${phoenixkey.cardano.lamp-policy-id:}")
    private String lampPolicyId;

    @Value("${phoenixkey.cardano.lamp-asset-name-hex:4c414d50}")
    private String lampAssetNameHex;

    @Value("${phoenixkey.cardano.magic-policy-id:}")
    private String magicPolicyId;

    @Value("${phoenixkey.cardano.magic-asset-name-hex:4d41474943}")
    private String magicAssetNameHex;

    public record AddressUtxos(long lovelace, long lampQuantity, long magicQuantity) {}

    private String baseUrl() {
        return switch (network) {
            case "mainnet" -> "https://cardano-mainnet.blockfrost.io/api/v0";
            case "preview" -> "https://cardano-preview.blockfrost.io/api/v0";
            default        -> "https://cardano-preprod.blockfrost.io/api/v0";
        };
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("project_id", blockfrostKey);
        return h;
    }

    public String submitSignedTx(String signedTxCbor) {
        try {
            HttpHeaders h = headers();
            h.setContentType(MediaType.APPLICATION_CBOR);
            byte[] cbor = hexToBytes(signedTxCbor);
            HttpEntity<byte[]> req = new HttpEntity<>(cbor, h);
            String txHash = restTemplate.postForObject(baseUrl() + "/tx/submit", req, String.class);
            if (txHash == null) throw new RuntimeException("Blockfrost returned empty body");
            String clean = txHash.replace("\"", "");
            log.info("Cardano tx submitted: {}", clean);
            return clean;
        } catch (Exception e) {
            throw new RuntimeException("Cardano submit error: " + e.getMessage(), e);
        }
    }

    public long getCurrentSlot() {
        try {
            HttpEntity<Void> req = new HttpEntity<>(headers());
            JsonNode res = restTemplate.exchange(baseUrl() + "/blocks/latest",
                    HttpMethod.GET, req, JsonNode.class).getBody();
            if (res == null) return 0;
            return res.path("slot").asLong();
        } catch (Exception e) {
            log.warn("Failed to fetch latest slot: {}", e.getMessage());
            return 0;
        }
    }

    public AddressUtxos getAddressUtxos(String address) {
        try {
            HttpEntity<Void> req = new HttpEntity<>(headers());
            JsonNode res = restTemplate.exchange(baseUrl() + "/addresses/" + address,
                    HttpMethod.GET, req, JsonNode.class).getBody();
            if (res == null) return new AddressUtxos(0, 0, 0);

            long lovelace = 0, lamp = 0, magic = 0;
            String lampUnit = lampPolicyId + lampAssetNameHex;
            String magicUnit = magicPolicyId + magicAssetNameHex;
            for (JsonNode amount : res.path("amount")) {
                String unit = amount.path("unit").asText();
                long qty = Long.parseLong(amount.path("quantity").asText("0"));
                if ("lovelace".equals(unit)) lovelace += qty;
                else if (unit.equals(lampUnit)) lamp += qty;
                else if (unit.equals(magicUnit)) magic += qty;
            }
            return new AddressUtxos(lovelace, lamp, magic);
        } catch (Exception e) {
            log.warn("Failed to fetch UTxOs for {}: {}", address, e.getMessage());
            return new AddressUtxos(0, 0, 0);
        }
    }

    private static byte[] hexToBytes(String hex) {
        String s = hex.startsWith("0x") ? hex.substring(2) : hex;
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
