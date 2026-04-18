package com.magiclamp.phoenixkey_db.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration cho PhoenixKey Database.
 *
 * Nguyên tắc:
 * - Stateless by default — không lưu session trên server
 * - JWT verification done by API Gateway (NestJS), không phải ở đây
 * - DB chỉ expose actuator health endpoint cho monitoring
 *
 * Lưu ý:
 * PhoenixKey Database là backend service, không phải public-facing API.
 * Security ở đây chủ yếu để:
 * - Block direct public access
 * - Cho phép API Gateway call vào
 * - Actuator endpoints cho health check
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless — không dùng session
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Disable CSRF (stateless API, no form login)
                .csrf(AbstractHttpConfigurer::disable)

                // Cấu hình quyền truy cập
                //
                // PhoenixKey Database là internal backend service — không public-facing.
                // JWT verification được thực hiện ở API Gateway (NestJS) trước khi request
                // đến đây. DB layer trust all requests đến từ internal network.
                //
                // Network-level protection: VPC/private subnet hoặc Kubernetes NetworkPolicy.
                // context-path = /api/v1 → Spring Security thấy path không có prefix /api/v1.
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll());

        return http.build();
    }
}
