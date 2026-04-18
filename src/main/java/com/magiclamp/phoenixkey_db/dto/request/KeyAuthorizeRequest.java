package com.magiclamp.phoenixkey_db.dto.request;

import com.magiclamp.phoenixkey_db.domain.KeyOriginType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * [V1.5] Request DTO cho {@code POST /api/v1/keys/authorize}.
 *
 * Thêm thiết bị/khóa mới cho một user.
 * Zero-Trust: {@code added_by_signature} phải được verify trước khi insert.
 * [V1.5] Nonce bắt buộc để chống Replay Attack.
 */
public record KeyAuthorizeRequest(
        /** DID của user sở hữu khóa. */
        @NotBlank(message = "User DID is required") String userDid,

        /** Public key hex từ Secure Enclave / TEE. */
        @NotBlank(message = "Public key hex is required") String publicKeyHex,

        /** [V1.5] Nguồn gốc của key. */
        @NotNull(message = "Key origin is required") KeyOriginType keyOrigin,

        /** owner | manager | viewer */
        @NotBlank(message = "Key role is required") String keyRole,

        /** [V1.5] Nonce bắt buộc — chống Replay Attack. */
        @NotBlank(message = "Nonce is required") String nonce,

        /** Zero-Trust: chữ ký từ root key, chứng minh user đồng ý. */
        @NotBlank(message = "Added by signature is required") String addedBySignature) {
}
