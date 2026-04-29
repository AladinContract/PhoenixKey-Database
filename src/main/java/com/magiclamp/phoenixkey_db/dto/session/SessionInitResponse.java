package com.magiclamp.phoenixkey_db.dto.session;

/**
 * Response cho {@code POST /auth/session/init}.
 *
 * <p>Web encode {@code sessionId + challenge + domain + expiresAt} thành QR
 * payload (base64url JSON), hiển thị cho user quét. Mobile decode → ký
 * challenge → gọi {@code /auth/session/{id}/approve}.</p>
 *
 * <p>{@code tempToken} dùng làm Bearer cho SSE stream — chứng minh client là
 * người tạo session này (chống ai tuỳ tiện listen SSE).</p>
 *
 * @param sessionId UUIDv7 (xem PLAN-Server.md §6.3)
 * @param challenge 32-byte random hex (chống replay)
 * @param tempToken JWT temp với sub=sessionId, TTL 5 phút
 * @param expiresAt epoch seconds, khi session hết hạn
 */
public record SessionInitResponse(
        String sessionId,
        String challenge,
        String tempToken,
        long expiresAt) {
}
