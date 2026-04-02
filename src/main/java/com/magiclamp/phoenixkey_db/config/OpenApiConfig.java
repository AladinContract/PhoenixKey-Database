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
 * Mọi endpoint đều require Authorization header.
 * JWT verification thực tế được thực hiện ở API Gateway (NestJS),
 * không phải ở tầng DB này.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path:/api/v1}")
    private String contextPath;

    @Bean
    public OpenAPI phoenixKeyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PhoenixKey Database API")
                        .version("v1.0")
                        .description(
                                """
                                        **PhoenixKey Database** — Identity Routing & Cache Hub.

                                        Vai trò duy nhất: trả lời nhanh câu hỏi *"User này là ai?"* và *"Khóa này có hợp lệ không?"*.

                                        ## Nguyên tắc thiết kế

                                        | Nguyên tắc | Mô tả |
                                        |---|---|
                                        | **Zero-PII** | Không lưu email/SĐT plaintext. Dùng Blind Index (HMAC-SHA256 + Pepper). |
                                        | **Stateless** | SSoT nằm trên Blockchain Cardano. DB chỉ là Indexer/Cache. |
                                        | **Decoupled** | Không lưu logic/cột nào của app khác (OriLife, AladinWork). |

                                        ## Security

                                        - JWT được verify bởi API Gateway (NestJS) trước khi request đến PK_DB
                                        - PK_DB nhận đã-authenticated request từ Gateway
                                        - Tất cả endpoint require `Authorization: Bearer <JWT>`

                                        ## Pepper Rotation

                                        Pepper (HMAC key) được đọc từ HashiCorp Vault tại `secret/phoenixkey/pepper`.
                                        Hỗ trợ multi-version: credential cũ vẫn verify được sau khi pepper rotate.
                                        """)
                        .contact(new Contact()
                                .name("MagicLamp Engineering")
                                .email("eng@magiclamp.io"))
                        .license(new License()
                                .name("Proprietary")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development"),
                        new Server()
                                .url("https://api.phoenixkey.magiclamp.io")
                                .description("Production")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(
                                        "JWT token — verify done by API Gateway (NestJS). PK_DB nhận đã-authenticated request.")));
    }
}
