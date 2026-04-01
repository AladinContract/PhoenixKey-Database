package com.magiclamp.phoenixkey_db.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO cho {@code POST /api/v1/keys/authorize}.
 *
 * <p>
 * Thêm thiết bị/khóa mới cho một user.
 * Zero-Trust: {@code added_by_signature} phải được verify trước khi insert.
 */
public record KeyAuthorizeRequest(
        /** DID của user sở hữu khóa. */
        @NotBlank(message = "User DID is required") String userDid,

        /** Public key hex từ Secure Enclave / TEE. */
        @NotBlank(message = "Public key hex is required") String publicKeyHex,

        /** owner | farm_manager | read_only */
        @NotBlank(message = "Key role is required") String keyRole,

        /** Zero-Trust: chữ ký từ root key, chứng minh user đồng ý. */
        @NotBlank(message = "Added by signature is required") String addedBySignature) {
}
