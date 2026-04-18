package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Nhật ký kiểm toán bất biến (Immutable Audit Trail).
 *
 * [V1.5] Refactor toàn bộ:
 * - Partitioned table (PARTITION BY RANGE created_at)
 * - Composite PK (id, created_at) — required cho partitioned table
 * - FK ON DELETE SET NULL (GDPR) — thay vì CASCADE
 * - Trigger thông minh smart_audit_trigger
 *
 * Append-Only với 3 nguyên tắc:
 * - [1] UPDATE: bị chặn (tính toàn vẹn pháp lý)
 * - [2] DELETE khi user_id IS NOT NULL: bị chặn (bảo vệ audit trail active)
 * - [3] DELETE khi user_id IS NULL: được phép (GDPR erasure hợp lệ)
 *
 * Các action thường gặy:
 * - Auth: {@code login_success}, {@code login_failed}, {@code otp_sent}, {@code otp_failed}
 * - Key: {@code key_authorized}, {@code key_revoked}, {@code key_rotated}
 * - Recovery: {@code recovery_initiated}, {@code recovery_cancelled}, {@code recovery_finalized}
 * - Guardian: {@code guardian_added}, {@code guardian_removed}
 * - Sync: {@code taad_synced}
 *
 * Lưu ý PII:
 * Cột {@code metadata} tuyệt đối KHÔNG được chứa PII
 * (số điện thoại, email, tên, địa chỉ IP thật).
 * Chỉ lưu: IP hash, OS version, device fingerprint hash.
 */
@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ActivityLogId.class)
public class ActivityLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * User thực hiện hành động.
     * [V1.5] Nullable — có thể là log trước khi user tồn tại (VD: failed login attempt).
     * [V1.5] FK ON DELETE SET NULL (GDPR) — khi user xóa tài khoản, log vẫn tồn tại.
     */
    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /**
     * Hành động xảy ra.
     * VD: {@code login_success}, {@code otp_sent}, {@code key_rotated}.
     */
    @Column(name = "action", length = 50, nullable = false)
    private String action;

    /**
     * Metadata linh hoạt dạng JSONB.
     *
     * TUYỆT ĐỐI KHÔNG CHỨA PII.
     *
     * Ví dụ metadata hợp lệ:
     * {
     *   "ip_hash": "sha256:abc123...",
     *   "os": "Android 14",
     *   "device_id_hash": "sha256:def456...",
     *   "provider": "email",
     *   "fail_reason": "invalid_otp"
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
