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
 * Vault KV v2 REST API: GET /v1/secret/data/<path>
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
     * Vault dev mode dùng HTTP (không HTTPS).
     */
    @Bean
    public RestTemplate vaultRestTemplate() {
        RestTemplate template = new RestTemplate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        template.setRequestFactory(factory);
        return template;
    }

    /** URI của Vault server. */
    public String getVaultUri() {
        return vaultUri;
    }
}
