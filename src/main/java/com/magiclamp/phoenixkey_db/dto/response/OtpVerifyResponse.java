package com.magiclamp.phoenixkey_db.dto.response;

import java.util.UUID;

/**
 * [V1.5] Response DTO cho {@code POST /api/v1/auth/otp/verify}.
 *
 * @param userDid    DID của user trên Blockchain Cardano
 * @param userId     [V1.5] UUID của user — dùng cho các operation tiếp theo
 * @param blindHash  Blind hash đã dùng để verify (HMAC-SHA256)
 * @param credential [V1.5] Credential gốc (email/phone) — dùng để re-hash
 *                   + gọi InvitationService.resolveOnRegistration()
 */
public record OtpVerifyResponse(
        String userDid,
        UUID userId,
        String blindHash,
        String credential) {
}