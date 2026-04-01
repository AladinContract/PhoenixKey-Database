package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mạng lưới bảo hộ khôi phục danh tính (Social Guardians).
 *
 * <p>
 * Khi user mất thiết bị/khóa, Guardian là người có thể hỗ trợ
 * khôi phục danh tính thông qua cơ chế TAAD (Social Recovery).
 *
 * <p>
 * ZERO-TRUST: {@code proof_signature} là chữ ký của User chứng minh
 * họ thực sự mời người này làm Guardian.
 * Backend phải verify chữ ký này trước khi INSERT.
 *
 * <p>
 * Constraint UNIQUE(user_id, guardian_did): mỗi user chỉ có 1 guardian_did 1
 * lần.
 */
@Entity
@Table(name = "guardians", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_guardian", columnNames = { "user_id", "guardian_did" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Guardian {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * User đang được bảo hộ.
     * ON DELETE CASCADE: xóa user → xóa hết guardians.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * DID của người được chỉ định làm Guardian.
     * Không dùng FK vì Guardian có thể là user chưa đăng ký trong hệ thống.
     */
    @Column(name = "guardian_did", length = 128, nullable = false)
    private String guardianDid;

    /**
     * ZERO-TRUST: Chữ ký của User chứng minh họ thực sự mời người này.
     * Backend PHẢI verify chữ ký này trước khi INSERT.
     */
    @Column(name = "proof_signature", length = 256, nullable = false)
    private String proofSignature;

    /**
     * Trạng thái: active | revoked.
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "active";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    public boolean isActive() {
        return "active".equals(status);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
