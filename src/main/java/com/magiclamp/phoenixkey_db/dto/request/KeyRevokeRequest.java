package com.magiclamp.phoenixkey_db.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * [V1.5] Request DTO cho {@code POST /api/v1/keys/revoke}.
 *
 * Thu hồi quyền của một thiết bị/khóa.
 * Zero-Trust: {@code signature} phải được verify trước khi revoke.
 * [V1.5] Nonce bắt buộc để chống Replay Attack.
 */
public record KeyRevokeRequest(
        @NotBlank(message = "User DID is required") String userDid,

        @NotBlank(message = "Public key hex is required") String publicKeyHex,

        /** [V1.5] Nonce bắt buộc — chống Replay Attack. */
        @NotBlank(message = "Nonce is required") String nonce,

        /** Chữ ký từ root key của user. */
        @NotBlank(message = "Signature is required") String signature) {
}
