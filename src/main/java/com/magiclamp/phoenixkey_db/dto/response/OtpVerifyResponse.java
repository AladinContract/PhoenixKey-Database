package com.magiclamp.phoenixkey_db.dto.response;

/**
 * Response DTO cho {@code POST /api/v1/auth/otp/verify}.
 *
 * <p>
 * OTP đã verify thành công. App dùng {@code user_did} để tạo session,
 * dùng {@code blind_hash} để associate với auth method tiếp theo.
 *
 * @param userDid   DID của user trên Blockchain Cardano
 * @param blindHash Blind hash đã dùng để verify (HMAC-SHA256)
 */
public record OtpVerifyResponse(
        String userDid,
        String blindHash) {
}
