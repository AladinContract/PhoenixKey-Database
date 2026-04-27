package com.magiclamp.phoenixkey_db.dto.sign;

/**
 * Response khi web tạo sign request.
 *
 * @param requestId UUIDv7 của sign request — web dùng để correlate event SSE
 * @param expiresAt epoch seconds, khi request hết hạn (TTL 120s)
 */
public record SignRequestCreateResponse(String requestId, long expiresAt) {
}
