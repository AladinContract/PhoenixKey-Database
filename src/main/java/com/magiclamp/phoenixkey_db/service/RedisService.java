package com.magiclamp.phoenixkey_db.service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service quản lý Redis cache cho Session + Sign-Request + Rate Limit.
 *
 * Key layout (xem PLAN-Server.md Phase D.4):
 * <ul>
 *   <li>{@code session:token:{jwt_hash}} — Legacy session (sẽ deprecate sau Phase D)</li>
 *   <li>{@code session:init:{sessionId}} — QR pairing state (challenge + status), TTL 5 phút</li>
 *   <li>{@code session:approved:{sessionId}} — Session token + linked-device sau khi mobile approve, TTL 24h</li>
 *   <li>{@code sign-req:{requestId}} — Sign request payload + signature, TTL 120s</li>
 *   <li>{@code linked-device:{token}} → userDid, TTL 30 ngày</li>
 *   <li>{@code ratelimit:ip:{ip_hash}} — Đếm request/IP, TTL 1 giờ</li>
 * </ul>
 *
 * Value format: JSON string (services serialize/deserialize qua ObjectMapper).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    @Value("${phoenixkey.session.ttl-seconds:86400}")
    private int sessionTtlSeconds;

    @Value("${phoenixkey.rate-limit.ttl-seconds:3600}")
    private int rateLimitTtlSeconds;

    private static final String RATE_LIMIT_PREFIX = "ratelimit:ip:";
    private static final String SESSION_PREFIX = "session:token:";
    private static final String SESSION_INIT_PREFIX = "session:init:";
    private static final String SESSION_APPROVED_PREFIX = "session:approved:";
    private static final String SIGN_REQ_PREFIX = "sign-req:";
    private static final String LINKED_DEVICE_PREFIX = "linked-device:";

    // ──────────────────────────────────────────────────────────────
    // Session
    // ──────────────────────────────────────────────────────────────

    /**
     * Lưu session vào Redis.
     *
     * @param jwtHash SHA-256 hash của JWT token
     * @param userDid DID của user
     * @param pubKey  public key hex
     */
    public void saveSession(String jwtHash, String userDid, String pubKey) {
        String key = SESSION_PREFIX + jwtHash;
        String value = userDid + "|" + pubKey;
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(sessionTtlSeconds));
    }

    /**
     * Lấy session từ Redis.
     *
     * @param jwtHash hash của JWT
     * @return map chứa userDid và pubKey
     */
    public Optional<Map<String, String>> getSession(String jwtHash) {
        String value = redisTemplate.opsForValue().get(SESSION_PREFIX + jwtHash);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|");
        if (parts.length != 2) {
            return Optional.empty();
        }
        return Optional.of(Map.of("userDid", parts[0], "pubKey", parts[1]));
    }

    /**
     * Xóa session (logout).
     *
     * @param jwtHash hash của JWT
     */
    public void deleteSession(String jwtHash) {
        redisTemplate.delete(SESSION_PREFIX + jwtHash);
    }

    // ──────────────────────────────────────────────────────────────
    // Rate Limit
    // ──────────────────────────────────────────────────────────────

    /**
     * Tăng số request từ một IP.
     *
     * @param ipHash SHA-256 hash của IP (không lưu IP thật)
     * @return số request hiện tại
     */
    public long incrementRateLimit(String ipHash) {
        String key = RATE_LIMIT_PREFIX + ipHash;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(rateLimitTtlSeconds));
        }
        return count != null ? count : 0;
    }

    /**
     * Kiểm tra IP có bị block chưa (vượt ngưỡng).
     *
     * @param ipHash     SHA-256 hash của IP
     * @param maxRequest số request tối đa cho phép
     * @return true nếu vượt ngưỡng
     */
    public boolean isRateLimited(String ipHash, int maxRequest) {
        String val = redisTemplate.opsForValue().get(RATE_LIMIT_PREFIX + ipHash);
        if (val == null) {
            return false;
        }
        return Long.parseLong(val) > maxRequest;
    }

    // ──────────────────────────────────────────────────────────────
    // Phase D — QR pairing session state (init/approved)
    // ──────────────────────────────────────────────────────────────

    /** session:init:{sessionId} — challenge + temp_token state, TTL 5 phút. */
    public void saveSessionInit(String sessionId, String json, Duration ttl) {
        redisTemplate.opsForValue().set(SESSION_INIT_PREFIX + sessionId, json, ttl);
    }

    public Optional<String> getSessionInit(String sessionId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(SESSION_INIT_PREFIX + sessionId));
    }

    public void deleteSessionInit(String sessionId) {
        redisTemplate.delete(SESSION_INIT_PREFIX + sessionId);
    }

    /** session:approved:{sessionId} — session_token + linkedDeviceToken sau approve, TTL 24h. */
    public void saveSessionApproved(String sessionId, String json, Duration ttl) {
        redisTemplate.opsForValue().set(SESSION_APPROVED_PREFIX + sessionId, json, ttl);
    }

    public Optional<String> getSessionApproved(String sessionId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(SESSION_APPROVED_PREFIX + sessionId));
    }

    // ──────────────────────────────────────────────────────────────
    // Phase D — Sign request relay
    // ──────────────────────────────────────────────────────────────

    /** sign-req:{requestId} — intent + payload + status, TTL 120s. */
    public void saveSignRequest(String requestId, String json, Duration ttl) {
        redisTemplate.opsForValue().set(SIGN_REQ_PREFIX + requestId, json, ttl);
    }

    public Optional<String> getSignRequest(String requestId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(SIGN_REQ_PREFIX + requestId));
    }

    public void deleteSignRequest(String requestId) {
        redisTemplate.delete(SIGN_REQ_PREFIX + requestId);
    }

    // ──────────────────────────────────────────────────────────────
    // Phase D — Linked-device token mapping
    // ──────────────────────────────────────────────────────────────

    /** linked-device:{token} → userDid, TTL 30 ngày. */
    public void saveLinkedDevice(String token, String userDid, Duration ttl) {
        redisTemplate.opsForValue().set(LINKED_DEVICE_PREFIX + token, userDid, ttl);
    }

    public Optional<String> getLinkedDevice(String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(LINKED_DEVICE_PREFIX + token));
    }

    public void deleteLinkedDevice(String token) {
        redisTemplate.delete(LINKED_DEVICE_PREFIX + token);
    }
}
