package com.magiclamp.phoenixkey_db.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO cho {@code POST /api/v1/keys/revoke}.
 *
 * Thu hồi quyền của một thiết bị/khóa.
 * Zero-Trust: {@code signature} phải được verify trước khi revoke.
 */
public record KeyRevokeRequest(
        @NotBlank(message = "User DID is required") String userDid,

        @NotBlank(message = "Public key hex is required") String publicKeyHex,

        /** Chữ ký từ root key của user. */
        @NotBlank(message = "Signature is required") String signature) {
}
