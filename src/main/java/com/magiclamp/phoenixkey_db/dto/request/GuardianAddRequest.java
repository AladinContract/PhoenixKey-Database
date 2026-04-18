package com.magiclamp.phoenixkey_db.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * [V1.5] Request DTO cho {@code POST /api/v1/guardians/add}.
 *
 * Thêm một guardian cho user.
 * Zero-Trust: {@code proof_signature} phải được verify trước khi insert.
 * [V1.5] Nonce bắt buộc để chống Replay Attack.
 */
public record GuardianAddRequest(
        /** DID của user được bảo hộ. */
        @NotBlank(message = "User DID is required") String userDid,

        /** DID của người được chỉ định làm guardian. */
        @NotBlank(message = "Guardian DID is required") String guardianDid,

        /** Zero-Trust: chữ ký từ user, chứng minh họ mời người này. */
        @NotBlank(message = "Proof signature is required") String proofSignature,

        /** [V1.5] Nonce bắt buộc — chống Replay Attack. */
        @NotBlank(message = "Nonce is required") String nonce) {
}