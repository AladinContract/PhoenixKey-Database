package com.magiclamp.phoenixkey_db.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * [V1.5] Request DTO cho {@code POST /api/v1/guardians/remove}.
 *
 * Xóa một guardian của user (soft revoke).
 * Sau khi remove, user có thể thêm guardian khác thay thế.
 */
public record GuardianRemoveRequest(
        /** DID của user được bảo hộ. */
        @NotBlank(message = "User DID is required")
        String userDid,

        /** DID của guardian cần xóa. */
        @NotBlank(message = "Guardian DID is required")
        String guardianDid,

        /** [V1.5] Nonce bắt buộc — chống Replay Attack. */
        @NotBlank(message = "Nonce is required") String nonce) {
}