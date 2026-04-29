package com.magiclamp.phoenixkey_db.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS config cho web client (phoenixkey.me + dev).
 *
 * <p>Origins lấy từ env {@code CORS_ALLOWED_ORIGINS} (comma-separated). Nếu
 * không set, fallback dev defaults: localhost:3000 + phoenixkey.me + staging.</p>
 *
 * <p>Phase D yêu cầu CORS đúng cho fetch/EventSource từ web. SSE qua
 * {@code GET /auth/session/{id}/stream} cần preflight pass + Authorization
 * header được expose.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${phoenixkey.cors.allowed-origins:http://localhost:3000,https://phoenixkey.me,https://staging.phoenixkey.me}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(86400);
    }
}
