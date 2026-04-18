package com.magiclamp.phoenixkey_db.dto.request;

import com.magiclamp.phoenixkey_db.domain.AuthProvider;
import com.magiclamp.phoenixkey_db.domain.KeyOriginType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * [V1.5] Request DTO cho {@code POST /api/v1/identity/register}.
 *
 * NestJS gọi khi App hoàn tất đăng ký, đã có:
 * - Credential đã verify OTP
 * - Public key hex từ Secure Enclave/TEE
 * - Chữ ký Zero-Trust từ root key
 *
 * [V1.5] keyOrigin bắt buộc — biết nguồn gốc key để quyết định
 * có dùng LampNet khi Recovery hay không.
 *
 * PK_DB:
 * - Hash credential → blind_hash
 * - Tạo UUIDv7 cho user_id
 * - Insert users + auth_methods + authorized_keys
 * - Trả về user_id + user_did (NestJS tự mint DID trên Cardano sau)
 */
public record IdentityRegisterRequest(
        @NotBlank(message = "Credential is required") String credential,

        @NotNull(message = "Provider is required") AuthProvider provider,

        /** Public key hex từ Secure Enclave / TEE chip. */
        @NotBlank(message = "Public key is required") String publicKeyHex,

        /** [V1.5] Nguồn gốc key. */
        @NotNull(message = "Key origin is required") KeyOriginType keyOrigin,

        /** owner | manager | viewer */
        @NotBlank(message = "Key role is required") String keyRole,

        /** Zero-Trust: chữ ký từ root key chứng minh user đồng ý. */
        @NotBlank(message = "Added by signature is required") String addedBySignature) {
}