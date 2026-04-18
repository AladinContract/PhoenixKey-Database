package com.magiclamp.phoenixkey_db.service;

import java.time.Duration;
import java.util.UUID;

/**
 * [V1.5] Service Discovery Bridge — quản lý pending invitations.
 *
 * Luồng Discovery Bridge:
 *   1. User A mời User B làm Guardian (User B chưa có app)
 *      → Tạo pending_invitation với invitee_blind_hash
 *
 *   2. User B đăng ký bằng SĐT/Email
 *      → Gọi resolveOnRegistration(blindHash, userId)
 *      → Auto-add guardian → mark invitation = 'resolved'
 *
 *   3. Cronjob markExpired() chạy mỗi giờ
 *      → Update status = 'expired' cho các lời mời quá hạn
 *
 * @see com.magiclamp.phoenixkey_db.service.impl.InvitationServiceImpl
 */
public interface InvitationService {

    /**
     * Tạo lời mời đang chờ (khi invitee chưa có app).
     *
     * @param inviterUserId      UUID của user mời
     * @param inviteeBlindHash   HMAC-SHA256 hash của SĐT/Email người được mời
     * @param inviteType         'guardian' | 'manager'
     * @param ttl               thời gian sống của lời mời
     */
    void createInvitation(UUID inviterUserId, String inviteeBlindHash, String inviteType, Duration ttl);

    /**
     * Auto-resolve các lời mời khi invitee đăng ký bằng SĐT/Email đã được mời.
     *
     * Được gọi trong IdentityService.register() sau khi tạo user thành công.
     *
     * @param inviteeBlindHash HMAC-SHA256 hash của SĐT/Email đã đăng ký
     * @param inviteeUserId    UUID của user vừa đăng ký
     */
    void resolveOnRegistration(String inviteeBlindHash, UUID inviteeUserId);

    /**
     * Mark các lời mời đã hết hạn.
     * Dùng cho cronjob chạy mỗi giờ.
     *
     * @return số dòng đã cập nhật
     */
    int markExpired();
}
