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
 * Append-Only — không ai được UPDATE hoặc DELETE.
 * Trigger {@code enforce_append_only} ở tầng Database enforce điều này.
 * Kể cả DBA cũng không được sửa hay xóa log.
 *
 * Các action thường gặy:
 * - Auth: {@code login_success}, {@code login_failed}, {@code otp_sent},
 * {@code otp_failed}
 * - Key: {@code key_authorized}, {@code key_revoked},
 * {@code key_rotated}
 * - Recovery: {@code recovery_initiated}, {@code recovery_cancelled},
 * {@code recovery_finalized}
 * - Guardian: {@code guardian_added}, {@code guardian_removed}
 * - Sync: {@code taad_synced}
 *
 * Lưu ý PII:
 * Cột {@code metadata} tuyệt đối KHÔNG được chứa PII
 * (số điện thoại, email, tên, địa chỉ IP thật).
 * Chỉ lưu: IP hash, OS version, device fingerprint hash.
 * 
 */
@Entity
@Table(name = "activity_logs", indexes = {
        @Index(name = "idx_activity_logs_user", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_activity_logs_action", columnList = "action, created_at DESC"),
        @Index(name = "idx_activity_logs_created", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * User thực hiện hành động.
     * Nullable — có thể là log trước khi user tồn tại (VD: failed login attempt).
     * Dùng userId cho logging (không cần load entity), dùng user cho JPA relation.
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
     *
     * {
     *   "ip_hash": "sha256:abc123...",
     *   "os": "Android 14",
     *   "device_id_hash": "sha256:def456...",
     *   "provider": "google",
     *   "fail_reason": "invalid_otp"
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * Thời điểm sự kiện xảy ra.
     * UUIDv7 đảm bảo sort theo thời gian tự nhiên.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}