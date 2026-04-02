package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.AuthMethod;
import com.magiclamp.phoenixkey_db.domain.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho {@link com.magiclamp.phoenixkey_db.domain.AuthMethod}.
 *
 * Đây là entry point chính của luồng đăng nhập:
 * tra cứu {@code blind_index_hash} → trả về user_did.
 */
@Repository
public interface AuthMethodRepository extends JpaRepository<AuthMethod, UUID> {

    // ──────────────────────────────────────────────────────────────
    // Tra cứu chính (dùng trong luồng đăng nhập)
    // ──────────────────────────────────────────────────────────────

    /**
     * Tìm auth method qua Blind Index Hash.
     * Đây là query quan trọng nhất của hệ thống — dùng trong luồng login.
     *
     * @param blindIndexHash HMAC-SHA256 hash của email/SĐT
     * @return Optional chứa AuthMethod nếu tìm thấy
     */
    Optional<AuthMethod> findByBlindIndexHash(String blindIndexHash);

    /**
     * Kiểm tra blind hash đã tồn tại chưa.
     *
     * @param blindIndexHash hash cần kiểm tra
     * @return true nếu đã tồn tại
     */
    boolean existsByBlindIndexHash(String blindIndexHash);

    // ──────────────────────────────────────────────────────────────
    // Tra cứu theo user
    // ──────────────────────────────────────────────────────────────

    /**
     * Tìm tất cả auth methods của một user.
     *
     * @param userId UUID của user
     * @return danh sách AuthMethod
     */
    List<AuthMethod> findByUserId(java.util.UUID userId);

    /**
     * Tìm auth method cụ thể theo user và provider.
     *
     * @param userId   UUID của user
     * @param provider nhà cung cấp (google, apple, phone)
     * @return Optional
     */
    Optional<AuthMethod> findByUserIdAndProvider(java.util.UUID userId, AuthProvider provider);

    /**
     * Kiểm tra user đã có auth method cho provider này chưa.
     *
     * @param userId   UUID của user
     * @param provider provider
     * @return true nếu đã có
     */
    boolean existsByUserIdAndProvider(java.util.UUID userId, AuthProvider provider);

    /**
     * Đếm số auth methods đã xác minh của một user.
     * Dùng cho security check: một user phải có ít nhất 1 auth method verified.
     *
     * @param userId UUID của user
     * @return số auth methods đã verify
     */
    long countByUserIdAndIsVerifiedTrue(java.util.UUID userId);

    // ──────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────

    /**
     * Xóa auth method bằng blind hash.
     * Dùng khi user xóa tài khoản (GDPR right to erasure).
     *
     * @param blindIndexHash hash cần xóa
     */
    void deleteByBlindIndexHash(String blindIndexHash);

    /**
     * Tìm tất cả auth methods cần re-hash khi pepper rotate.
     * Pepper version cũ hơn current → cần re-hash ở lần đăng nhập tiếp theo.
     *
     * @param oldVersion phiên bản pepper cũ
     * @return danh sách AuthMethod cần re-hash
     */
    @Query("SELECT a FROM AuthMethod a WHERE a.pepperVersion < :oldVersion")
    List<AuthMethod> findAllNeedingPepperMigration(@Param("oldVersion") int oldVersion);
}
