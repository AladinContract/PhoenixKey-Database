package com.magiclamp.phoenixkey_db.dto.sign;

import jakarta.validation.constraints.NotBlank;

/**
 * Mobile approve sign request — gọi {@code POST /sign/{id}/approve}.
 *
 * <p>Sau khi user xem intent + biometric unlock, mobile ký intent JSON (canonical
 * form) bằng Hardware Key. Server verify → emit SSE event tới web với signature.</p>
 *
 * <p>Message ký: JSON canonical của {@link SignIntent} (key sorted, no whitespace).
 * Mobile và server dùng cùng canonicalization (Jackson default order trong record).</p>
 *
 * @param publicKeyHex Hardware Key pubkey
 * @param signature    DER-encoded ECDSA secp256k1 signature trên canonical intent JSON
 */
public record SignApproveRequest(
        @NotBlank String publicKeyHex,
        @NotBlank String signature) {
}
