package com.magiclamp.phoenixkey_db.service.secret;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * KMS chung cho mọi secret high-value của PhoenixKey-Server.
 *
 * Vault layout (KV v2 mounted at {@code secret/}):
 * <pre>
 * secret/phoenixkey/
 * ├── fee-wallet/mnemonic     { "words": ["word1", ..., "word24"] }
 * ├── jwt/secret              { "key": "&lt;base64-32-byte&gt;" }
 * ├── fcm/service-account     { "json": "&lt;Firebase service account JSON&gt;" }
 * ├── apns/auth-key           { "key": "&lt;.p8 content&gt;", "keyId": "...", "teamId": "..." }
 * └── blockfrost/api-key      { "key": "preprodXXX..." }
 * </pre>
 *
 * Dev fallback: nếu {@code spring.cloud.vault.enabled=false}, đọc từ env vars
 * (chỉ áp dụng cho fee-wallet + jwt; FCM/APNs phải qua Vault hoặc disabled).
 *
 * Thread-safety: load 1 lần ở {@link #init()}, sau đó immutable getter.
 * Auto-renew Vault token mỗi giờ qua {@link #checkTokenHealth()}.
 */
@Service
@Slf4j
public class VaultSecretService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final boolean vaultEnabled;
    private final String vaultUri;
    private final String vaultToken;
    private final String vaultNamespace;
    private final String envFeeWalletMnemonic;
    private final String envJwtSecret;

    private String[] feeWalletMnemonic;
    private byte[] jwtSecret;

    public VaultSecretService(
            RestTemplate vaultRestTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.cloud.vault.enabled:true}") boolean vaultEnabled,
            @Value("${spring.cloud.vault.uri:http://localhost:8200}") String vaultUri,
            @Value("${spring.cloud.vault.token:}") String vaultToken,
            @Value("${spring.cloud.vault.namespace:}") String vaultNamespace,
            @Value("${FEE_WALLET_MNEMONIC:}") String envFeeWalletMnemonic,
            @Value("${PHOENIXKEY_JWT_SECRET:}") String envJwtSecret) {
        this.restTemplate = vaultRestTemplate;
        this.objectMapper = objectMapper;
        this.vaultEnabled = vaultEnabled;
        this.vaultUri = vaultUri;
        this.vaultToken = vaultToken;
        this.vaultNamespace = vaultNamespace;
        this.envFeeWalletMnemonic = envFeeWalletMnemonic;
        this.envJwtSecret = envJwtSecret;
    }

    @PostConstruct
    void init() {
        if (vaultEnabled) {
            log.info("VaultSecretService: loading secrets from Vault at {}", vaultUri);
            this.feeWalletMnemonic = loadFeeWalletMnemonicFromVault();
            this.jwtSecret = loadJwtSecretFromVault();
        } else {
            log.warn("Vault disabled — reading secrets from env vars (DEV ONLY)");
            this.feeWalletMnemonic = loadFeeWalletMnemonicFromEnv();
            this.jwtSecret = loadJwtSecretFromEnv();
        }
        log.info("VaultSecretService initialized: mnemonic_words={}, jwt_secret_bytes={}",
                feeWalletMnemonic.length, jwtSecret.length);
    }

    // ──────────────────────────────────────────────────────────────
    // Public getters
    // ──────────────────────────────────────────────────────────────

    /** Mnemonic 24 từ cho fee wallet. */
    public String[] getFeeWalletMnemonic() {
        return feeWalletMnemonic.clone();
    }

    /** HMAC key 32 bytes cho JWT. */
    public byte[] getJwtSecret() {
        return jwtSecret.clone();
    }

    /**
     * Generic raw KV v2 reader. Path không bao gồm prefix {@code /v1/secret/data/}.
     * Ví dụ: {@code phoenixkey/fcm/service-account}.
     */
    public Map<String, Object> readSecret(String kvPath) {
        if (!vaultEnabled) {
            throw new IllegalStateException(
                    "Vault disabled — readSecret() requires Vault enabled");
        }
        return readKvV2(kvPath);
    }

    // ──────────────────────────────────────────────────────────────
    // Vault loaders
    // ──────────────────────────────────────────────────────────────

    private String[] loadFeeWalletMnemonicFromVault() {
        Map<String, Object> data = readKvV2("phoenixkey/fee-wallet/mnemonic");
        Object words = data.get("words");
        if (words instanceof List<?> list) {
            return list.stream().map(String::valueOf).toArray(String[]::new);
        }
        if (words instanceof String s) {
            // Legacy format: space-separated 24 words
            return s.trim().split("\\s+");
        }
        throw new IllegalStateException(
                "Vault path 'phoenixkey/fee-wallet/mnemonic' missing 'words' field");
    }

    private byte[] loadJwtSecretFromVault() {
        Map<String, Object> data = readKvV2("phoenixkey/jwt/secret");
        Object key = data.get("key");
        if (!(key instanceof String s) || s.isBlank()) {
            throw new IllegalStateException(
                    "Vault path 'phoenixkey/jwt/secret' missing 'key' field");
        }
        return Base64.getDecoder().decode(s);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readKvV2(String kvPath) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            String url = vaultUri + "/v1/secret/data/" + kvPath;
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data").path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new IllegalStateException(
                        "Vault path '" + kvPath + "' not found or empty");
            }
            return objectMapper.convertValue(data, Map.class);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to read Vault path '" + kvPath + "': " + e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Env fallback (dev only)
    // ──────────────────────────────────────────────────────────────

    private String[] loadFeeWalletMnemonicFromEnv() {
        if (envFeeWalletMnemonic == null || envFeeWalletMnemonic.isBlank()) {
            throw new IllegalStateException(
                    "Vault disabled but FEE_WALLET_MNEMONIC env not set");
        }
        return envFeeWalletMnemonic.trim().split("\\s+");
    }

    private byte[] loadJwtSecretFromEnv() {
        if (envJwtSecret == null || envJwtSecret.isBlank()) {
            byte[] random = new byte[32];
            SECURE_RANDOM.nextBytes(random);
            log.warn("PHOENIXKEY_JWT_SECRET not set — generated ephemeral random secret. "
                    + "All session tokens invalidate on restart.");
            return random;
        }
        return Base64.getDecoder().decode(envJwtSecret);
    }

    // ──────────────────────────────────────────────────────────────
    // Token auto-renew
    // ──────────────────────────────────────────────────────────────

    /**
     * Mỗi giờ kiểm tra TTL token. Nếu < 7 ngày → tự renew.
     * Yêu cầu token được tạo với {@code -renewable=true}.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void checkTokenHealth() {
        if (!vaultEnabled) {
            return;
        }
        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(
                    vaultUri + "/v1/auth/token/lookup-self",
                    HttpMethod.GET, entity, String.class);

            long ttlSeconds = objectMapper.readTree(resp.getBody())
                    .path("data").path("ttl").asLong();
            long hoursLeft = ttlSeconds / 3600;

            if (hoursLeft < 168) {
                log.warn("Vault token TTL low: {}h left → auto-renewing", hoursLeft);
                renewToken();
            } else {
                log.debug("Vault token TTL healthy: {}h left", hoursLeft);
            }
        } catch (Exception e) {
            log.error("Failed to check Vault token TTL: {}", e.getMessage());
        }
    }

    /** Renew token với increment 720h. Gọi từ {@link #checkTokenHealth()}. */
    void renewToken() {
        try {
            HttpHeaders headers = buildAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>("{\"increment\":\"720h\"}", headers);
            restTemplate.exchange(
                    vaultUri + "/v1/auth/token/renew-self",
                    HttpMethod.POST, entity, String.class);
            log.info("Vault token renewed successfully (TTL reset to 720h)");
        } catch (Exception e) {
            log.error("Failed to renew Vault token — manual intervention may be needed: {}",
                    e.getMessage());
        }
    }

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", vaultToken);
        if (vaultNamespace != null && !vaultNamespace.isBlank()) {
            headers.set("X-Vault-Namespace", vaultNamespace);
        }
        return headers;
    }

    /** Wipe in-memory secrets at shutdown. */
    public void shutdown() {
        if (jwtSecret != null) {
            Arrays.fill(jwtSecret, (byte) 0);
        }
        if (feeWalletMnemonic != null) {
            Arrays.fill(feeWalletMnemonic, "");
        }
    }
}
