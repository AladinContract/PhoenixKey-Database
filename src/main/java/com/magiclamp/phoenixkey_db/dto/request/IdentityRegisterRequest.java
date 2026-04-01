package com.magiclamp.phoenixkey_db.dto.request;

import com.magiclamp.phoenixkey_db.domain.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO cho {@code POST /api/v1/identity/register}.
 *
 * <p>
 * NestJS gọi khi App hoàn tất đăng ký, đã có:
 * <ul>
 * <li>Credential đã verify OTP</li>
 * <li>Public key hex từ Secure Enclave/TEE</li>
 * <li>Chữ ký Zero-Trust từ root key</li>
 * </ul>
 *
 * <p>
 * PK_DB:
 * <ol>
 * <li>Hash credential → blind_hash</li>
 * <li>Tạo UUIDv7 cho user_id</li>
 * <li>Insert users + auth_methods + authorized_keys</li>
 * <li>Trả về user_id + user_did (NestJS tự mint DID trên Cardano sau)</li>
 * </ol>
 */
public record IdentityRegisterRequest(
		@NotBlank(message = "Credential is required") String credential,

		@NotNull(message = "Provider is required") AuthProvider provider,

		/** Public key hex từ Secure Enclave / TEE chip. */
		@NotBlank(message = "Public key is required") String publicKeyHex,

		/** Vai trò: owner | farm_manager | read_only */
		@NotBlank(message = "Key role is required") String keyRole,

		/** Zero-Trust: chữ ký từ root key chứng minh user đồng ý. */
		@NotBlank(message = "Added by signature is required") String addedBySignature) {
}
