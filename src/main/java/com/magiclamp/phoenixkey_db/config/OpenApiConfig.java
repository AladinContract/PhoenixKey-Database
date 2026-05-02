package com.magiclamp.phoenixkey_db.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger UI configuration.
 *
 * Live UI:  https://api.phoenixkey.me/api/v1/swagger-ui.html
 * JSON:     https://api.phoenixkey.me/api/v1/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path:/api/v1}")
    private String contextPath;

    @Bean
    public OpenAPI phoenixKeyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PhoenixKey-Server API")
                        .version("v1.0")
                        .description(
                                """
                                        **PhoenixKey-Server** — backend duy nhất cho mobile (Aladin app) và web (phoenixkey.me) + 3rd-party app trong hệ sinh thái Aladin (OriLife, AladinWork, ...).

                                        ## Vai trò

                                        | Layer | Trách nhiệm |
                                        |---|---|
                                        | **Identity provider** | Đăng ký + resolve DID (`did:cardano:<network>:<txHash>`), pubkey lookup |
                                        | **Auth relay** | QR pairing + SSE cho web login, sign-request relay cho web ↔ mobile |
                                        | **Cardano gateway** | Build + submit DID Document tx qua BloxBean + Blockfrost |
                                        | **Audit trail** | Activity logs (Zero-PII), nonce replay protection |

                                        ## Nguyên tắc

                                        - **SSoT** = Cardano blockchain. PostgreSQL chỉ là indexer cache cho TAAD state.
                                        - **Zero key custody**: Hardware Key của user nằm trong Secure Enclave/TEE mobile, server không bao giờ thấy private key.
                                        - **Zero-PII**: không lưu email/SĐT/password — auth chỉ qua Hardware Key signature.

                                        ## Auth

                                        Mỗi endpoint mutation yêu cầu `Authorization: Bearer <token>`. 3 loại token:

                                        | Token | TTL | Cấp ở |
                                        |---|---|---|
                                        | `temp_token` | 5 phút | `POST /auth/session/init` — dùng cho SSE stream + status polling |
                                        | `session_token` | 24 giờ | `POST /auth/session/{id}/approve` — dùng cho mọi mutation |
                                        | `linked_device_token` | 30 ngày | `POST /auth/session/{id}/approve` — push-to-device thay QR |

                                        ## SDK

                                        - TypeScript: `@phoenixkeydid/phoenixkey-sdk` (browser + Node)
                                        - Verifier sub-package: `@phoenixkeydid/phoenixkey-sdk/verifier` (3rd-party backend verify chữ ký locally)
                                        - Java + Swift SDK: roadmap khi có consumer thật.
                                        """)
                        .contact(new Contact()
                                .name("MagicLamp Engineering")
                                .email("eng@magiclamp.io"))
                        .license(new License()
                                .name("Proprietary")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080/api/v1")
                                .description("Local development"),
                        new Server()
                                .url("https://api.phoenixkey.me/api/v1")
                                .description("Production")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(
                                        "Bearer JWT — verify trong từng controller qua JwtService.parseAndVerify().")));
    }
}
