package com.magiclamp.phoenixkey_db.service;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Đọc pepper (hiện tại + lịch sử) từ HashiCorp Vault.
 *
 * Vault path: secret/phoenixkey/pepper
 * Format:
 * {
 * "current_version": 2,
 * "pepper_1": "pepper_cũ_6tháng_trước",
 * "pepper_2": "pepper_hiện_tại"
 * }
 *
 * Khi pepper được rotate (6 tháng/lần), Vault chứa cả pepper cũ lẫn mới.
 * PK_DB đọc tất cả vào memory khi khởi động, dùng để verify/re-hash.
 *
 * Zero-PII: Pepper là HMAC key bảo mật — không log, không lưu vào DB.
 */
@Service
@Slf4j
public class PepperVaultService {

    private final VaultTemplate vaultTemplate;
    private final Map<Integer, String> pepperByVersion = new TreeMap<>();

    /** Phiên bản pepper hiện tại (lớn nhất). */
    private int currentVersion;

    private static final String VAULT_PATH = "secret/phoenixkey/pepper";

    public PepperVaultService(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
        loadPeppers();
    }

    /**
     * Load tất cả pepper từ Vault vào memory.
     * Gọi 1 lần khi khởi động.
     */
    private void loadPeppers() {
        try {
            VaultResponse response = vaultTemplate.read(VAULT_PATH);
            if (response == null || response.getData() == null) {
                throw new IllegalStateException(
                        "Vault path '" + VAULT_PATH + "' not found or empty. "
                                + "Run: vault kv put " + VAULT_PATH
                                + " current_version=1 pepper_1='<your_pepper>'");
            }

            Map<String, Object> data = response.getData();
            this.currentVersion = ((Number) data.get("current_version")).intValue();

            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("pepper_") && entry.getValue() != null) {
                    int version = Integer.parseInt(key.substring("pepper_".length()));
                    pepperByVersion.put(version, (String) entry.getValue());
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

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load peppers from Vault at '" + VAULT_PATH + "': " + e.getMessage(), e);
        }
    }

    /**
     * Lấy pepper của một version cụ thể.
     *
     * @param version pepper version
     * @return pepper string
     * @throws IllegalStateException nếu version không tồn tại
     */
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
