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
                .authorizeHttpRequests(auth -> auth
                        // Actuator health — ai cũng đọc được (load balancer health check)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Actuator info — chỉ internal
                        .requestMatchers("/actuator/info").permitAll()
                        // Mọi endpoint API — yêu cầu authenticated
                        // (JWT verify được thực hiện ở API Gateway, không ở DB layer)
                        .requestMatchers("/api/v1/**").authenticated()
                        // Mọi thứ khác — deny
                        .anyRequest().denyAll());

        return http.build();
    }
}
