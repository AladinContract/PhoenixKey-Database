package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.PendingInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * [V1.5] Repository cho {@link com.magiclamp.phoenixkey_db.domain.PendingInvitation}.
 *
 * Discovery Bridge: quản lý lời mời Guardian khi người được mời chưa có app.
 */
@Repository
public interface PendingInvitationRepository extends JpaRepository<PendingInvitation, UUID> {

    /**
     * Tìm lời mời đang chờ theo invitee blind hash.
     * Dùng khi invitee đăng ký → tự động resolve.
     *
     * @param blindHash HMAC-SHA256 hash của SĐT/Email
     * @param status    trạng thái ('pending')
     * @return danh sách lời mời
     */
    List<PendingInvitation> findByInviteeBlindHashAndStatus(String blindHash, String status);

    /**
     * Tìm tất cả lời mời của một user.
     *
     * @param inviterUserId UUID của user mời
     * @return danh sách lời mời
     */
    List<PendingInvitation> findByInviterUserId(UUID inviterUserId);

    /**
     * Mark lời mời đã hết hạn.
     *
     * @param now thời điểm hiện tại
     * @return số dòng đã cập nhật
     */
    @Modifying
    @Query("UPDATE PendingInvitation p SET p.status = 'expired' " +
            "WHERE p.status = 'pending' AND p.expiresAt < :now")
    int markExpired(@Param("now") OffsetDateTime now);

    /**
     * Xóa lời mời đã resolved hoặc expired.
     * Dùng cho cleanup sau khi đã resolve.
     *
     * @param id ID của lời mời
     */
    @Modifying
    @Query("DELETE FROM PendingInvitation p WHERE p.id = :id AND p.status <> 'pending'")
    void deleteResolved(@Param("id") UUID id);
}
