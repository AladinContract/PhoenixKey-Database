package com.magiclamp.phoenixkey_db.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.magiclamp.phoenixkey_db.domain.ActivityLog;
import com.magiclamp.phoenixkey_db.repository.ActivityLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service quản lý activity logs.
 *
 * <p>
 * TUYỆT ĐỐI không có method update/delete — logs là append-only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    // ──────────────────────────────────────────────────────────────
    // Action constants
    // ──────────────────────────────────────────────────────────────

    public static final String ACTION_OTP_SENT = "otp_sent";
    public static final String ACTION_OTP_FAILED = "otp_failed";
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

    private void save(UUID userId, String action, Map<String, Object> metadata) {
        ActivityLog logEntry = ActivityLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .action(action)
                .metadata(metadata)
                .build();
        activityLogRepository.save(logEntry);
    }
}
