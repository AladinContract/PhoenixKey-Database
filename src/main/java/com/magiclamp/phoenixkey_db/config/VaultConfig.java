package com.magiclamp.phoenixkey_db.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * HashiCorp Vault configuration.
 *
 * Dùng RestTemplate gọi Vault HTTP API trực tiếp — không cần spring-vault-core.
 * Hỗ trợ:
 * - HTTP (dev mode)
 * - HTTPS (HCP Vault / self-hosted prod)
 * - Vault Namespace (HCP Vault multi-tenant)
 *
 * Nguyên tắc bảo mật:
 * - Pepper tuyệt đối KHÔNG được lưu trong file .env / hardcode / git.
 * - Token được đọc từ environment variable.
 */
@Configuration
public class VaultConfig {

    @Value("${spring.cloud.vault.uri:http://localhost:8200}")
    private String vaultUri;

    /**
     * RestTemplate để gọi Vault HTTP API.
     * Tự động dùng HTTPS nếu URI bắt đầu bằng https://
     */
    @Bean
    public RestTemplate vaultRestTemplate() {
        RestTemplate template = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        template.setRequestFactory(factory);
        return template;
    }

    public String getVaultUri() {
        return vaultUri;
    }
}
