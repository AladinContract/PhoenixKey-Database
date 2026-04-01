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
 * <p>
 * <b>Append-Only — không ai được UPDATE hoặc DELETE.</b>
 * Trigger {@code enforce_append_only} ở tầng Database enforce điều này.
 * Kể cả DBA cũng không được sửa hay xóa log.
 *
 * <p>
 * Các action thường gặy:
 * <ul>
 * <li>Auth: {@code login_success}, {@code login_failed}, {@code otp_sent},
 * {@code otp_failed}</li>
 * <li>Key: {@code key_authorized}, {@code key_revoked},
 * {@code key_rotated}</li>
 * <li>Recovery: {@code recovery_initiated}, {@code recovery_cancelled},
 * {@code recovery_finalized}</li>
 * <li>Guardian: {@code guardian_added}, {@code guardian_removed}</li>
 * <li>Sync: {@code taad_synced}</li>
 * </ul>
 *
 * <p>
 * <b>Lưu ý PII:</b>
 * Cột {@code metadata} tuyệt đối KHÔNG được chứa PII
 * (số điện thoại, email, tên, địa chỉ IP thật).
 * Chỉ lưu: IP hash, OS version, device fingerprint hash.
 *
 * @see <a href="https://phoenixkey.magiclamp.internal/docs/audit-trail">Audit
 *      Trail
 *      Design</a>
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
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
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
     * <p>
     * TUYỆT ĐỐI KHÔNG CHỨA PII.
     *
     * <p>
     * Ví dụ metadata hợp lệ:
     *
     * <pre>
     * {
     *   "ip_hash": "sha256:abc123...",
     *   "os": "Android 14",
     *   "device_id_hash": "sha256:def456...",
     *   "provider": "google",
     *   "fail_reason": "invalid_otp"
     * }
     * </pre>
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