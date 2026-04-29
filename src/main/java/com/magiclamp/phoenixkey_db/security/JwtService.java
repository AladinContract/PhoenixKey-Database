package com.magiclamp.phoenixkey_db.security;

import io.jsonwebtoken.Claims;

import java.time.Duration;

/**
 * Mint + verify JWT cho session management (Phase D).
 *
 * Token types:
 * <ul>
 *   <li><b>temp</b> — issued ở {@code /auth/session/init}, dùng để authorize
 *       SSE stream của session đó. TTL 5 phút.</li>
 *   <li><b>session</b> — issued sau khi mobile approve. Dùng làm Bearer cho
 *       các API của user. TTL 24h.</li>
 *   <li><b>linked-device</b> — issued kèm session token, lưu localStorage cho
 *       phép web push notification về mobile mà không cần QR. TTL 30 ngày.</li>
 * </ul>
 *
 * HMAC key load từ Vault qua {@code VaultSecretService.getJwtSecret()} ở init.
 */
public interface JwtService {

    /** Mint token cho QR-pairing session (sub=sessionId, type=temp). */
    String mintTempToken(String sessionId, Duration ttl);

    /** Mint session token sau khi mobile approve (sub=userDid, type=session). */
    String mintSessionToken(String userDid, Duration ttl);

    /** Mint linked-device token (sub=userDid, type=linked-device, TTL 30d). */
    String mintLinkedDeviceToken(String userDid);

    /**
     * Parse + verify token. Throws nếu invalid/expired.
     *
     * @return Claims (sub, type, iat, exp, ...)
     */
    Claims parseAndVerify(String token);

    /** Convenience: extract sub claim sau verify. */
    default String parseSubject(String token) {
        return parseAndVerify(token).getSubject();
    }

    /** Convenience: extract type claim sau verify. */
    default String parseType(String token) {
        return parseAndVerify(token).get("type", String.class);
    }
}
