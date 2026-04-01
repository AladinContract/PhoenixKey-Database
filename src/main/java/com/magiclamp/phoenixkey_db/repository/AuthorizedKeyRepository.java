package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.AuthorizedKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho {@link com.magiclamp.phoenixkey_db.domain.AuthorizedKey}.
 *
 * <p>
 * Lưu ý: FK dùng {@code user_did} (VARCHAR) nên query chủ yếu theo DID string.
 */
@Repository
public interface AuthorizedKeyRepository extends JpaRepository<AuthorizedKey, UUID> {

    // ──────────────────────────────────────────────────────────────
    // Tra cứu theo user (DID)
    // ──────────────────────────────────────────────────────────────

    /**
     * Tìm tất cả khóa active của một user.
     *
     * @param userDid DID string
     * @return danh sách khóa active
     */
    @Query("SELECT k FROM AuthorizedKey k WHERE k.user.userDid = :userDid AND k.status = 'active'")
    List<AuthorizedKey> findActiveByUserDid(@Param("userDid") String userDid);

    /**
     * Tìm tất cả khóa (kể cả revoked) của một user.
     *
     * @param userDid DID string
     * @return danh sách tất cả khóa
     */
    List<AuthorizedKey> findByUserUserDid(String userDid);

    /**
     * Kiểm tra public key đã được authorized cho user này chưa.
     *
     * @param userDid      DID string
     * @param publicKeyHex public key hex
     * @return true nếu đã authorized
     */
    boolean existsByUserUserDidAndPublicKeyHexAndStatus(
            String userDid, String publicKeyHex, String status);

    // ──────────────────────────────────────────────────────────────
    // Tra cứu theo public key
    // ──────────────────────────────────────────────────────────────

    /**
     * Tìm khóa qua public key hex.
     *
     * @param publicKeyHex public key hex
     * @return Optional
     */
    Optional<AuthorizedKey> findByPublicKeyHex(String publicKeyHex);

    /**
     * Kiểm tra public key đã tồn tại trong hệ thống chưa.
     *
     * @param publicKeyHex public key hex
     * @return true nếu tồn tại
     */
    boolean existsByPublicKeyHex(String publicKeyHex);

    // ──────────────────────────────────────────────────────────────
    // Quản lý trạng thái
    // ──────────────────────────────────────────────────────────────

    /**
     * Revoke một khóa (soft delete — không xóa record).
     *
     * @param publicKeyHex public key cần revoke
     */
    @Modifying
    @Query("UPDATE AuthorizedKey k SET k.status = 'revoked' WHERE k.publicKeyHex = :publicKeyHex")
    void revokeByPublicKeyHex(@Param("publicKeyHex") String publicKeyHex);

    /**
     * Revoke tất cả khóa của một user (dùng khi user bị compromise toàn diện).
     *
     * @param userDid DID string
     * @return số khóa bị revoke
     */
    @Modifying
    @Query("UPDATE AuthorizedKey k SET k.status = 'revoked' " +
            "WHERE k.user.userDid = :userDid AND k.status = 'active'")
    int revokeAllByUserDid(@Param("userDid") String userDid);

    /**
     * Đếm số khóa active của một user.
     *
     * @param userDid DID string
     * @return số khóa active
     */
    @Query("SELECT COUNT(k) FROM AuthorizedKey k " +
            "WHERE k.user.userDid = :userDid AND k.status = 'active'")
    long countActiveByUserDid(@Param("userDid") String userDid);

    /**
     * Tìm khóa owner active của user.
     *
     * @param userDid DID string
     * @return Optional chứa owner key
     */
    @Query("SELECT k FROM AuthorizedKey k " +
            "WHERE k.user.userDid = :userDid AND k.keyRole = 'owner' AND k.status = 'active'")
    Optional<AuthorizedKey> findOwnerByUserDid(@Param("userDid") String userDid);
}