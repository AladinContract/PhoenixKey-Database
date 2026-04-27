package com.magiclamp.phoenixkey_db.dto.session;

import jakarta.validation.constraints.NotBlank;

/**
 * Request cho {@code POST /auth/session/{id}/approve} từ mobile.
 *
 * <p>Mobile sau khi quét QR + biometric unlock + ký challenge bằng Hardware Key,
 * gọi endpoint này. Server verify signature → mint session_token + linked_device_token,
 * push qua SSE để web nhận.</p>
 *
 * <p>Message ký: {@code challenge + ":" + domain + ":" + timestamp} (giữ
 * structure đơn giản — domain bind chống cross-domain replay, timestamp đã có
 * trong challenge nhưng repeat để rõ ràng).</p>
 *
 * @param userDid       DID của user trên mobile
 * @param publicKeyHex  Hardware Key pubkey (compressed/uncompressed)
 * @param signature     DER-encoded ECDSA secp256k1 signature
 * @param domain        domain web request đến (vd "phoenixkey.me") — bind chống cross-site
 * @param timestamp     epoch seconds lúc mobile ký (chống replay với challenge cũ)
 */
public record SessionApproveRequest(
        @NotBlank String userDid,
        @NotBlank String publicKeyHex,
        @NotBlank String signature,
        @NotBlank String domain,
        long timestamp) {
}
