package com.magiclamp.phoenixkey_db.dto.identity;

import com.magiclamp.phoenixkey_db.domain.KeyOriginType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO cho {@code POST /api/v1/identity/register}.
 *
 * Mobile gọi sau khi sinh Hardware Key trong Secure Enclave/TEE. Server:
 * 1. Verify Genesis signature trên payload {@code "PHOENIXKEY_GENESIS:" + publicKeyHex}
 * 2. Publish DID Document lên Cardano qua {@code CardanoService.createDID()}
 * 3. Insert {@code users} + {@code authorized_keys} (1-step, không còn pending)
 *
 * Bỏ {@code credential}/{@code provider}/{@code blindHash} — flow mới Zero-PII
 * không lưu email/SĐT.
 */
public record IdentityRegisterRequest(
        /** Public key hex từ Secure Enclave / TEE chip. */
        @NotBlank(message = "Public key is required") String publicKeyHex,

        /** Nguồn gốc key — quyết định luồng Recovery. */
        @NotNull(message = "Key origin is required") KeyOriginType keyOrigin,

        /** owner | manager | viewer */
        @NotBlank(message = "Key role is required") String keyRole,

        /**
         * Genesis signature (DER-encoded ECDSA SECP256K1) từ Hardware Key,
         * ký trên message {@code "PHOENIXKEY_GENESIS:" + publicKeyHex}.
         */
        @NotBlank(message = "Added by signature is required") String addedBySignature) {
}
