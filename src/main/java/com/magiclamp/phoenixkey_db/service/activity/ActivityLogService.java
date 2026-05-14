package com.magiclamp.phoenixkey_db.service.activity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.magiclamp.phoenixkey_db.domain.ActivityLog;
import com.magiclamp.phoenixkey_db.repository.ActivityLogRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service quản lý activity logs.
 *
 * TUYỆT ĐỐI không có method update/delete — logs là append-only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    /** Metadata key for SHA-256-hashed client IP (Zero-PII per spec §10.1). */
    private static final String IP_HASH_KEY = "ip_hash";

    // ──────────────────────────────────────────────────────────────
    // Action constants
    // ──────────────────────────────────────────────────────────────

    public static final String ACTION_LOGIN_SUCCESS = "login_success";
    public static final String ACTION_LOGIN_FAILED = "login_failed";
    public static final String ACTION_USER_REGISTERED = "user_registered";
    public static final String ACTION_KEY_AUTHORIZED = "key_authorized";
    public static final String ACTION_KEY_REVOKED = "key_revoked";
    public static final String ACTION_KEY_ROTATED = "key_rotated";
    public static final String ACTION_GUARDIAN_ADDED = "guardian_added";
    public static final String ACTION_GUARDIAN_REMOVED = "guardian_removed";
    public static final String ACTION_RECOVERY_APPROVED = "recovery_approved";
    public static final String ACTION_TAAD_SYNCED = "taad_synced";
    /** [V1.5+] Web QR pair approved bởi mobile. */
    public static final String ACTION_WEB_SESSION_APPROVED = "web_session_approved";
    /** [V1.5+] Sign request từ web được mobile approve. */
    public static final String ACTION_SIGN_REQUEST_APPROVED = "sign_request_approved";
    /** [V1.5+] Sign request bị mobile reject. */
    public static final String ACTION_SIGN_REQUEST_REJECTED = "sign_request_rejected";
    /** [V1.5+] User trích xuất Seed Phrase (spec §9). */
    public static final String ACTION_SEED_PHRASE_EXPORTED = "seed_phrase_exported";
    /** [V1.5+] Web tạo sign request (trước khi mobile approve/reject). */
    public static final String ACTION_SIGN_REQUEST_INITIATED = "sign_request_initiated";

    // ──────────────────────────────────────────────────────────────
    // Log methods
    // ──────────────────────────────────────────────────────────────

    /**
     * Ghi log không liên kết user (trước khi user tồn tại).
     */
    public void log(String action, Map<String, Object> metadata) {
        save(null, action, metadata);
    }

    /**
     * Ghi log với user.
     */
    public void log(UUID userId, String action, Map<String, Object> metadata) {
        save(userId, action, metadata);
    }

    /**
     * Ghi log nhanh với metadata đơn giản.
     */
    public void log(UUID userId, String action, String key, String value) {
        save(userId, action, Map.of(key, value));
    }

    /**
     * Convenience overload — log by DID string. Looks up user lazily.
     * Use this from services that only have the DID handy (e.g. ActivationService,
     * RecoveryService, BalanceService).
     */
    public void log(String userDid, String action, String detail) {
        UUID userId = userRepository.findByUserDid(userDid)
                .map(com.magiclamp.phoenixkey_db.domain.User::getId)
                .orElse(null);
        save(userId, action, detail == null || detail.isEmpty()
                ? Map.of()
                : Map.of("detail", detail));
    }

    private void save(UUID userId, String action, Map<String, Object> metadata) {
        ActivityLog logEntry = ActivityLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .action(action)
                .metadata(enrichWithIpHash(metadata))
                .build();
        activityLogRepository.save(logEntry);
    }

    /**
     * Spec §10.1 Zero-PII: capture client IP → SHA-256 → metadata.ip_hash.
     *
     * <p>Đọc HttpServletRequest từ {@link RequestContextHolder} (chỉ available
     * trong request scope). Nếu được gọi ngoài request (vd: Indexer Worker,
     * scheduled task) → trả metadata nguyên — không throw.</p>
     *
     * <p>Lookup order: {@code X-Forwarded-For} (proxy/LB) trước, fallback
     * {@code remoteAddr}. ActivityLogController.maskIpInMetadata() sẽ truncate
     * 8 char đầu khi serve cho dashboard.</p>
     */
    private Map<String, Object> enrichWithIpHash(Map<String, Object> metadata) {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return metadata;
            }
            String clientIp = extractClientIp(attrs.getRequest());
            if (clientIp == null || clientIp.isBlank()) {
                return metadata;
            }
            String hashed = sha256Hex(clientIp);
            Map<String, Object> enriched =
                    (metadata == null) ? new HashMap<>() : new HashMap<>(metadata);
            enriched.put(IP_HASH_KEY, hashed);
            return enriched;
        } catch (Exception e) {
            log.debug("ip_hash capture failed (non-fatal): {}", e.getMessage());
            return metadata;
        }
    }

    private static String extractClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For có thể là chuỗi "client, proxy1, proxy2" — lấy phần đầu.
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private static String sha256Hex(String s) {
        // SHA-256 luôn có sẵn trong JCE chuẩn (java.security spec) — wrap
        // NoSuchAlgorithmException thành unchecked để giữ method signature gọn.
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
