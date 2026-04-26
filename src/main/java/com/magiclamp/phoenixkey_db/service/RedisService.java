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
 * Service quản lý Redis cache cho Session + Rate Limit.
 *
 * Hai mục đích chính:
 * - {@code session:token:{jwt_hash}} — Phiên đăng nhập web, TTL 24 giờ
 * - {@code ratelimit:ip:{ip_hash}} — Đếm request/IP, TTL 1 giờ
 *
 * Phase D sẽ thêm: {@code session:init:*}, {@code sign-req:*}, {@code linked-device:*}.
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
}
