package com.magiclamp.phoenixkey_db.dto.key;

import com.magiclamp.phoenixkey_db.domain.KeyOriginType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO cho {@code POST /api/v1/keys/rotate}.
 *
 * Mobile gọi khi user muốn rotate Hardware Key (vd: lo ngại bị compromise, hoặc
 * sau khi trích xuất Seed Phrase). Server:
 * 1. Verify {@code oldKeySignature} trên payload {@code newPublicKeyHex + nonce}
 *    bằng public key cũ (đảm bảo người yêu cầu có private key cũ).
 * 2. Consume nonce chống replay.
 * 3. Build Cardano updateDID tx (consume UTxO genesis + tạo UTxO mới với datum
 *    chứa pubkey mới). MVP: fee wallet ký full tx.
 * 4. Cập nhật DB: revoke key cũ + insert key mới.
 *
 * Spec §11. Phase H sẽ yêu cầu mobile cung cấp partial-signed CBOR từ old key
 * thay vì để fee wallet ký (Zero-Trust enforcement).
 */
public record KeyRotateRequest(
        /** DID của user (used để find owner key cũ + extract previous tx hash). */
        @NotBlank(message = "User DID is required") String userDid,

        /** Public key hex mới (thay thế key cũ). */
        @NotBlank(message = "New public key hex is required") String newPublicKeyHex,

        /** Nguồn gốc key mới. */
        @NotNull(message = "Key origin is required") KeyOriginType keyOrigin,

        /** Nonce — chống Replay Attack. */
        @NotBlank(message = "Nonce is required") String nonce,

        /**
         * DER-encoded ECDSA signature từ key CŨ trên message
         * {@code "PHOENIXKEY_ROTATE:" + newPublicKeyHex + ":" + nonce}.
         * Chứng minh người gọi sở hữu private key cũ.
         */
        @NotBlank(message = "Old key signature is required") String oldKeySignature) {
}
