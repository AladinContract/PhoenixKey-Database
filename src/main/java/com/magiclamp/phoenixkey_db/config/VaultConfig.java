package com.magiclamp.phoenixkey_db.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * HashiCorp Vault configuration — quản lý SERVER_PEPPER.
 *
 * <p>
 * <b>Nguyên tắc bảo mật tuyệt đối:</b>
 * SERVER_PEPPER dùng để tạo Blind Index hash cho email/SĐT.
 * Pepper tuyệt đối KHÔNG được:
 * <ul>
 * <li>Lưu trong file .env</li>
 * <li>Hardcode trong source code</li>
 * <li>Gửi lên Git repository</li>
 * </ul>
 *
 * <p>
 * <b>Cơ chế hoạt động:</b>
 * <ul>
 * <li>Production: {@code spring.cloud.vault.enabled=true} + token →
 * Spring Cloud Vault auto-configure
 * {@link org.springframework.vault.core.VaultTemplate}
 * và đọc pepper từ path {@code secret/phoenixkey/server_pepper}</li>
 * <li>Local dev: {@code spring.cloud.vault.enabled=false} → fallback sang
 * property {@code phoenixkey.pepper} trong application.yml</li>
 * </ul>
 *
 * <p>
 * <b>Pepper Rotation (6 tháng/lần):</b>
 * Khi pepper xoay vòng trên Vault:
 * <ol>
 * <li>Tăng pepper_version lên 2, 3...</li>
 * <li>Hash cũ vẫn verify được (multi-version support)</li>
 * <li>Lần đăng nhập tiếp theo, hệ thống re-hash với pepper mới</li>
 * </ol>
 *
 * @see <a href="https://phoenixkey.magiclamp.internal/docs/vault-ops">Vault
 *      Operations</a>
 */
@Configuration
public class VaultConfig {

    @Value("${spring.cloud.vault.enabled:false}")
    private boolean vaultEnabled;

    @Value("${spring.cloud.vault.uri:http://localhost:8200}")
    private String vaultUri;

    @Value("${spring.cloud.vault.token:}")
    private String vaultToken;

    @Value("${spring.cloud.vault.kv.backend:secret}")
    private String kvBackend;

    @Value("${spring.cloud.vault.kv.default-context:phoenixkey}")
    private String kvContext;

    /**
     * Kiểm tra Vault có được enable không.
     *
     * @return true nếu dùng Vault (production)
     */
    public boolean isVaultEnabled() {
        return vaultEnabled;
    }
}
