package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.Guardian;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository cho {@link com.magiclamp.phoenixkey_db.domain.Guardian}.
 *
 * Guardian là mạng lưới bảo hộ khôi phục danh tính.
 * Mỗi user nên có 3–5 guardian. Ít hơn thì không đủ an toàn,
 * nhiều hơn thì rủi ro guardian malicious cao.
 */
@Repository
public interface GuardianRepository extends JpaRepository<Guardian, UUID> {

    // ──────────────────────────────────────────────────────────────
    // Tra cứu theo user
    // ──────────────────────────────────────────────────────────────

    /**
     * Tìm tất cả guardian active của một user.
     *
     * @param userId UUID của user
     * @return danh sách guardian active
     */
    @Query("SELECT g FROM Guardian g WHERE g.user.id = :userId AND g.status = 'active'")
    List<Guardian> findActiveByUserId(@Param("userId") UUID userId);

    /**
     * Tìm guardian active bằng guardian DID.
     *
     * @param guardianDid DID của guardian
     * @return danh sách bản ghi (1 user có thể có nhiều guardian)
     */
    @Query("SELECT g FROM Guardian g WHERE g.guardianDid = :guardianDid AND g.status = 'active'")
    List<Guardian> findActiveByGuardianDid(@Param("guardianDid") String guardianDid);

    /**
     * Kiểm tra một DID đã là guardian của user này chưa.
     *
     * @param userId      UUID của user
     * @param guardianDid DID cần kiểm tra
     * @return true nếu đã là guardian
     */
    boolean existsByUserIdAndGuardianDidAndStatus(
            UUID userId, String guardianDid, String status);

    // ──────────────────────────────────────────────────────────────
    // Quản lý trạng thái
    // ──────────────────────────────────────────────────────────────

    /**
     * Revoke một guardian (soft delete).
     *
     * @param userId      UUID của user
     * @param guardianDid DID của guardian cần revoke
     */
    @Modifying
    @Query("UPDATE Guardian g SET g.status = 'revoked' " +
            "WHERE g.user.id = :userId AND g.guardianDid = :guardianDid AND g.status = 'active'")
    int revokeByUserIdAndGuardianDid(
            @Param("userId") UUID userId,
            @Param("guardianDid") String guardianDid);

    /**
     * Đếm guardian active của một user.
     *
     * @param userId UUID của user
     * @return số guardian active
     */
    @Query("SELECT COUNT(g) FROM Guardian g WHERE g.user.id = :userId AND g.status = 'active'")
    long countActiveByUserId(@Param("userId") UUID userId);

    /**
     * Kiểm tra user đã đạt ngưỡng guardian tối thiểu chưa (>= 3).
     *
     * @param userId UUID của user
     * @return true nếu có đủ guardian
     */
    @Query("SELECT COUNT(g) >= 3 FROM Guardian g " +
            "WHERE g.user.id = :userId AND g.status = 'active'")
    boolean hasMinimumGuardians(@Param("userId") UUID userId);
}
