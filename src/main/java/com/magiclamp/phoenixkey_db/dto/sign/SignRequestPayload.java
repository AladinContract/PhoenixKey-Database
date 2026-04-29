package com.magiclamp.phoenixkey_db.dto.sign;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Mobile fetch payload qua {@code GET /sign/request/{id}} sau khi nhận push.
 *
 * <p>Spec §15.5: push payload KHÔNG chứa intent thật, chỉ chứa requestId. Mobile
 * fetch chi tiết qua HTTPS authenticated để hiển thị cho user.</p>
 *
 * @param requestId UUIDv7
 * @param userDid   DID của user (mobile self-check)
 * @param sessionId session web đã tạo request (để mobile tracking)
 * @param intent    PhoenixKey Signing Standard intent
 * @param status    pending | approved | rejected | expired
 * @param expiresAt epoch seconds
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignRequestPayload(
        String requestId,
        String userDid,
        String sessionId,
        SignIntent intent,
        String status,
        long expiresAt) {
}
