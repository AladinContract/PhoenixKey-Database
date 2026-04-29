package com.magiclamp.phoenixkey_db.security;

import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.service.secret.VaultSecretService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JJWT-based implementation. HMAC-SHA256 với secret 32-byte từ Vault.
 *
 * <p>Type claim phân biệt token cho mỗi flow để tránh dùng nhầm cross-flow
 * (vd: temp_token không thể dùng cho API yêu cầu session).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtServiceImpl implements JwtService {

    public static final String TYPE_TEMP = "temp";
    public static final String TYPE_SESSION = "session";
    public static final String TYPE_LINKED_DEVICE = "linked-device";

    /** TTL cho linked-device token (30 ngày). */
    private static final Duration LINKED_DEVICE_TTL = Duration.ofDays(30);

    private final VaultSecretService vaultSecretService;
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] secret = vaultSecretService.getJwtSecret();
        if (secret == null || secret.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must be ≥ 32 bytes for HS256, got " + (secret == null ? 0 : secret.length));
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
        log.info("JwtServiceImpl initialized with HS256 ({}-byte secret)", secret.length);
    }

    @Override
    public String mintTempToken(String sessionId, Duration ttl) {
        return mint(sessionId, TYPE_TEMP, ttl);
    }

    @Override
    public String mintSessionToken(String userDid, Duration ttl) {
        return mint(userDid, TYPE_SESSION, ttl);
    }

    @Override
    public String mintLinkedDeviceToken(String userDid) {
        return mint(userDid, TYPE_LINKED_DEVICE, LINKED_DEVICE_TTL);
    }

    @Override
    public Claims parseAndVerify(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT parse/verify failed: {}", e.getMessage());
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "Invalid or expired JWT");
        }
    }

    private String mint(String subject, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}
