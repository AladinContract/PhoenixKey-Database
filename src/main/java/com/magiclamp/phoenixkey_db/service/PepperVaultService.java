package com.magiclamp.phoenixkey_db.service;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Đọc pepper (hiện tại + lịch sử) từ HashiCorp Vault.
 *
 * Vault path: secret/phoenixkey/pepper
 * Format:
 * {
 * "current_version": 2,
 * "pepper_1": "pepper_cũ",
 * "pepper_2": "pepper_hiện_tại"
 * }
 *
 * Dùng RestTemplate gọi Vault HTTP API trực tiếp.
 * Khi pepper rotate (6 tháng/lần), Vault chứa cả pepper cũ lẫn mới.
 * PK_DB đọc tất cả vào memory khi khởi động.
 *
 * Zero-PII: Pepper là HMAC key bảo mật — không log, không lưu vào DB.
 */
@Service
@Slf4j
public class PepperVaultService {

    private final RestTemplate restTemplate;
    private final String vaultUri;
    private final String vaultToken;
    private final String vaultNamespace;
    private final ObjectMapper objectMapper;
    private final Map<Integer, String> pepperByVersion = new TreeMap<>();

    /** Phiên bản pepper hiện tại (lớn nhất). */
    private int currentVersion;

    private static final String VAULT_PATH = "secret/phoenixkey/pepper";

    public PepperVaultService(
            RestTemplate vaultRestTemplate,
            @Value("${spring.cloud.vault.uri:http://localhost:8200}") String vaultUri,
            @Value("${spring.cloud.vault.token:}") String vaultToken,
            @Value("${spring.cloud.vault.namespace:}") String vaultNamespace,
            ObjectMapper objectMapper) {
        this.restTemplate = vaultRestTemplate;
        this.vaultUri = vaultUri;
        this.vaultToken = vaultToken;
        this.vaultNamespace = vaultNamespace;
        this.objectMapper = objectMapper;
        loadPeppers();
    }

    /**
     * Load tất cả pepper từ Vault vào memory.
     * Gọi 1 lần khi khởi động.
     *
     * Vault KV v2 response format:
     * {
     * "data": {
     * "data": { "current_version": "1", "pepper_1": "..." },
     * "metadata": { ... }
     * }
     * }
     */
    private void loadPeppers() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Vault-Token", vaultToken);
            // HCP Vault dùng namespace header
            if (vaultNamespace != null && !vaultNamespace.isBlank()) {
                headers.set("X-Vault-Namespace", vaultNamespace);
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Vault KV v2
            String url = vaultUri + "/v1/secret/data/phoenixkey/pepper";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data").path("data");

            if (data.isMissingNode() || data.isNull()) {
                throw new IllegalStateException(
                        "Vault path '" + VAULT_PATH + "' not found or empty. "
                                + "Run: vault kv put " + VAULT_PATH
                                + " current_version=1 pepper_1='<your_pepper>'");
            }

            this.currentVersion = data.path("current_version").asInt();

            // Load all peppers (pepper_1, pepper_2, ...)
            java.util.Iterator<String> fieldNames = data.fieldNames();
            while (fieldNames.hasNext()) {
                String key = fieldNames.next();
                if (key.startsWith("pepper_") && !data.get(key).isNull()) {
                    int version = Integer.parseInt(key.substring("pepper_".length()));
                    pepperByVersion.put(version, data.get(key).asText());
                }
            }

            String currentPepper = pepperByVersion.get(currentVersion);
            if (currentPepper == null) {
                throw new IllegalStateException(
                        "Pepper for current version " + currentVersion
                                + " not found in Vault. Check '" + VAULT_PATH + "'.");
            }

            log.info("PepperVaultService initialized: current_version={}, versions_stored={}",
                    currentVersion, pepperByVersion.keySet());

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load peppers from Vault at '" + VAULT_PATH + "': " + e.getMessage(), e);
        }
    }

    /** Lấy pepper của một version cụ thể. */
    public String getPepper(int version) {
        String pepper = pepperByVersion.get(version);
        if (pepper == null) {
            throw new IllegalStateException(
                    "Pepper version " + version + " not found. "
                            + "Available versions: " + pepperByVersion.keySet());
        }
        return pepper;
    }

    /** Phiên bản pepper hiện tại. */
    public int getCurrentVersion() {
        return currentVersion;
    }

    /** Pepper hiện tại (dùng khi hash credential mới). */
    public String getCurrentPepper() {
        return pepperByVersion.get(currentVersion);
    }

    /** Kiểm tra version có tồn tại trong Vault không. */
    public boolean hasVersion(int version) {
        return pepperByVersion.containsKey(version);
    }
}
