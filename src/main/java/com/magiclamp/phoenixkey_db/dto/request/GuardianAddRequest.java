package com.magiclamp.phoenixkey_db.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO cho {@code POST /api/v1/guardians/add}.
 *
 * Thêm một guardian cho user.
 * Zero-Trust: {@code proof_signature} phải được verify trước khi insert.
 */
public record GuardianAddRequest(
        /** DID của user được bảo hộ. */
        @NotBlank(message = "User DID is required") String userDid,

        /** DID của người được chỉ định làm guardian. */
        @NotBlank(message = "Guardian DID is required") String guardianDid,

        /** Zero-Trust: chữ ký từ user, chứng minh họ mời người này. */
        @NotBlank(message = "Proof signature is required") String proofSignature) {
}
