package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * [V1.5] Discovery Bridge — lưu lời mời Guardian khi người được mời chưa có app.
 *
 * Luồng Discovery Bridge:
 *   1. User A nhập SĐT/Email của User B để mời làm Guardian
 *   2. Backend tính blind_index_hash của User B
 *   3. Nếu User B chưa có app → ghi vào pending_invitations
 *   4. Khi User B đăng ký bằng SĐT/Email đó:
 *      → Backend match blind_index_hash
 *      → Tự động resolve: ghi guardian_did vào guardians
 *      → Xóa dòng pending_invitations
 *
 * @see InvitationService
 */
@Entity
@Table(name = "pending_invitations", indexes = {
    @Index(name = "idx_invitee_hash", columnList = "invitee_blind_hash"),
    @Index(name = "idx_inviter_user", columnList = "inviter_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingInvitation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * User mời (inviter).
     * ON DELETE CASCADE: xóa user → xóa hết lời mời đã gửi.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_user_id", nullable = false)
    private User inviterUser;

    /**
     * Blind hash của người được mời (invitee).
     * HMAC_SHA256(phone_or_email, SERVER_PEPPER).
     */
    @Column(name = "invitee_blind_hash", nullable = false, length = 64)
    private String inviteeBlindHash;

    /**
     * Loại lời mời: 'guardian' | 'manager'
     */
    @Column(name = "invite_type", nullable = false, length = 20)
    private String inviteType;

    /**
     * Thời điểm lời mời hết hạn.
     * Cronjob markExpired chạy mỗi giờ để cập nhật status = 'expired'.
     */
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /**
     * Trạng thái: 'pending' | 'resolved' | 'expired'
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    public boolean isPending() {
        return "pending".equals(status);
    }

    public boolean isExpired() {
        return expiresAt != null && OffsetDateTime.now().isAfter(expiresAt);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}