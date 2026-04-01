package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository cho {@link com.magiclamp.phoenixkey_db.domain.ActivityLog}.
 *
 * <p>
 * <b>Chỉ INSERT — tuyệt đối không UPDATE/DELETE.</b>
 * Trigger {@code enforce_append_only} ở tầng DB sẽ block mọi sửa/xóa.
 *
 * <p>
 * Tất cả query chỉ đọc (SELECT). Các method {@code delete*} chỉ dùng cho:
 * <ul>
 * <li>GDPR: xóa user → cascade xóa log (trigger sẽ block, dùng batch delete thủ
 * công nếu cần)</li>
 * <li>Data retention: xóa log cũ hơn N ngày (chạy định kỳ, cần DBA
 * approve)</li>
 * </ul>
 */
@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    // ──────────────────────────────────────────────────────────────
    // Tra cứu theo user
    // ──────────────────────────────────────────────────────────────

    /**
     * Lấy log của một user, sorted theo thời gian giảm dần (mới nhất trước).
     *
     * @param userId   UUID của user
     * @param pageable phân trang
     * @return trang log
     */
    Page<ActivityLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Lấy log gần nhất của một user.
     *
     * @param userId UUID của user
     * @param limit  số bản ghi
     * @return danh sách log
     */
    @Query("SELECT a FROM ActivityLog a WHERE a.user.id = :userId " +
            "ORDER BY a.createdAt DESC LIMIT :limit")
    List<ActivityLog> findLatestByUserId(
            @Param("userId") UUID userId,
            @Param("limit") int limit);

    // ──────────────────────────────────────────────────────────────
    // Tra cứu theo action
    // ──────────────────────────────────────────────────────────────

    /**
     * Lấy tất cả log của một action type, sorted mới nhất trước.
     *
     * @param action   action name (VD: {@code login_failed})
     * @param pageable phân trang
     * @return trang log
     */
    Page<ActivityLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    /**
     * Đếm số lần xảy ra một action trong khoảng thời gian.
     * Dùng cho rate limit checking và anomaly detection.
     *
     * @param action    action name
     * @param startTime bắt đầu
     * @param endTime   kết thúc
     * @return số lần xảy ra
     */
    @Query("SELECT COUNT(a) FROM ActivityLog a " +
            "WHERE a.action = :action " +
            "AND a.createdAt >= :startTime AND a.createdAt <= :endTime")
    long countByActionInRange(
            @Param("action") String action,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime);

    // ──────────────────────────────────────────────────────────────
    // Tra cứu theo thời gian (audit)
    // ──────────────────────────────────────────────────────────────

    /**
     * Lấy tất cả log trong khoảng thời gian (dùng cho audit/security review).
     *
     * @param startTime bắt đầu
     * @param endTime   kết thúc
     * @param pageable  phân trang
     * @return trang log
     */
    Page<ActivityLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            Pageable pageable);

    /**
     * Tìm log theo user và action trong khoảng thời gian.
     * Dùng để kiểm tra anomaly (VD: quá nhiều failed login).
     *
     * @param userId    UUID của user
     * @param action    action name
     * @param startTime bắt đầu
     * @param endTime   kết thúc
     * @return danh sách log
     */
    @Query("SELECT a FROM ActivityLog a " +
            "WHERE a.user.id = :userId AND a.action = :action " +
            "AND a.createdAt >= :startTime AND a.createdAt <= :endTime " +
            "ORDER BY a.createdAt DESC")
    List<ActivityLog> findByUserIdAndActionInRange(
            @Param("userId") UUID userId,
            @Param("action") String action,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime);

    // ──────────────────────────────────────────────────────────────
    // Security / Anomaly detection
    // ──────────────────────────────────────────────────────────────

    /**
     * Đếm số failed login gần đây của một IP hash.
     * Dùng cho rate limiting (block IP nếu vượt ngưỡng).
     *
     * @param ipHash SHA-256 hash của IP (không lưu IP thật)
     * @param since  thời điểm bắt đầu
     * @return số failed login
     */
    @Query("SELECT COUNT(a) FROM ActivityLog a " +
            "WHERE a.action = 'login_failed' " +
            "AND a.metadata['ip_hash'] = :ipHash " +
            "AND a.createdAt >= :since")
    long countRecentFailedLogins(
            @Param("ipHash") String ipHash,
            @Param("since") OffsetDateTime since);
}
