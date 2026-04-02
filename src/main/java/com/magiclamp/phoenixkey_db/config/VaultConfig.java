package com.magiclamp.phoenixkey_db.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * HashiCorp Vault configuration — quản lý SERVER_PEPPER.
 *
 * Nguyên tắc bảo mật tuyệt đối:
 * - SERVER_PEPPER dùng để tạo Blind Index hash cho email/SĐT.
 * Pepper tuyệt đối KHÔNG được:
 * - Lưu trong file .env
 * - Hardcode trong source code
 * - Gửi lên Git repository
 *
 * Cơ chế hoạt động:
 * - Production: {@code spring.cloud.vault.enabled=true} + token →
 * Spring Cloud Vault auto-configure
 * {@link org.springframework.vault.core.VaultTemplate}
 * và đọc pepper từ path {@code secret/phoenixkey/server_pepper}
 * - Local dev: {@code spring.cloud.vault.enabled=false} → fallback sang
 * property {@code phoenixkey.pepper} trong application.yml
 *
 * Pepper Rotation (6 tháng/lần):
 * Khi pepper xoay vòng trên Vault:
 * - Tăng pepper_version lên 2, 3...
 * - Hash cũ vẫn verify được (multi-version support)
 * - Lần đăng nhập tiếp theo, hệ thống re-hash với pepper mới
 *
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
