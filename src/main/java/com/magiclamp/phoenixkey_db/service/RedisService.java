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
 * Service quản lý Redis cache cho OTP, Session, Rate Limit.
 *
 * Ba mục đích:
 * - {@code otp:auth:{blind_hash}} — Lưu OTP, TTL 5 phút
 * - {@code ratelimit:ip:{ip_hash}} — Đếm request/IP, TTL 1 giờ
 * - {@code session:token:{jwt_hash}} — Phiên đăng nhập, TTL 24 giờ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    @Value("${phoenixkey.otp.ttl-seconds:300}")
    private int otpTtlSeconds;

    @Value("${phoenixkey.session.ttl-seconds:86400}")
    private int sessionTtlSeconds;

    @Value("${phoenixkey.rate-limit.ttl-seconds:3600}")
    private int rateLimitTtlSeconds;

    private static final String OTP_PREFIX = "otp:auth:";
    private static final String RATE_LIMIT_PREFIX = "ratelimit:ip:";
    private static final String SESSION_PREFIX = "session:token:";

    private static final String KEY_SUFFIX = ":attempts";

    // ──────────────────────────────────────────────────────────────
    // OTP
    // ──────────────────────────────────────────────────────────────

    /**
     * Lưu OTP vào Redis.
     *
     * @param blindHash HMAC-SHA256 hash của credential
     * @param otp       Mã OTP 6 chữ số
     */
    public void saveOtp(String blindHash, String otp) {
        String key = OTP_PREFIX + blindHash;
        redisTemplate.opsForValue().set(key, otp, Duration.ofSeconds(otpTtlSeconds));
        log.debug("OTP saved for blind_hash={}, ttl={}s", blindHash, otpTtlSeconds);
    }

    /**
     * Lấy OTP từ Redis.
     *
     * @param blindHash blind hash của credential
     * @return OTP nếu còn, empty nếu hết hạn
     */
    public Optional<String> getOtp(String blindHash) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(OTP_PREFIX + blindHash));
    }

    /**
     * Xóa OTP khỏi Redis (sau khi verify thành công hoặc hết hạn).
     *
     * @param blindHash blind hash của credential
     */
    public void deleteOtp(String blindHash) {
        redisTemplate.delete(OTP_PREFIX + blindHash);
    }

    /**
     * Tăng số lần nhập sai OTP.
     *
     * @param blindHash blind hash của credential
     * @return số lần sai hiện tại
     */
    public long incrementOtpAttempts(String blindHash) {
        String key = OTP_PREFIX + blindHash + KEY_SUFFIX;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofSeconds(otpTtlSeconds));
        return count != null ? count : 0;
    }

    /**
     * Reset số lần nhập sai OTP.
     *
     * @param blindHash blind hash của credential
     */
    public void resetOtpAttempts(String blindHash) {
        redisTemplate.delete(OTP_PREFIX + blindHash + KEY_SUFFIX);
    }

    /**
     * Lấy số lần sai OTP hiện tại.
     *
     * @param blindHash blind hash của credential
     * @return số lần sai
     */
    public long getOtpAttempts(String blindHash) {
        String val = redisTemplate.opsForValue().get(OTP_PREFIX + blindHash + KEY_SUFFIX);
        return val != null ? Long.parseLong(val) : 0;
    }

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
