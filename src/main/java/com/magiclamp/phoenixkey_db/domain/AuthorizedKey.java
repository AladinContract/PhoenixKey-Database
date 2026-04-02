package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Quản lý đa khóa / đa thiết bị & định tuyến LampNet.
 *
 * Một User (DID) có thể có nhiều thiết bị/khóa:
 *   - owner: thiết bị gốc, toàn quyền
 *   - farm_manager: ký giao dịch liên quan đến farm
 *   - read_only: chỉ đọc, không ký
 *
 * ZERO-TRUST: {@code added_by_signature} là chữ ký từ Root Key.
 * NestJS (Backend) verify chữ ký này trước khi gọi PK_DB INSERT.
 * Nếu Backend bị hack, hacker không thể tự ý thêm khóa vì không có Root Key.
 * PK_DB chỉ nhận và lưu — không verify được vì không có root public key.
 *
 * Lưu ý: Bảng này dùng user_did (VARCHAR) làm FK thay vì user_id (UUID),
 * vì authorized_keys được truy vấn chủ yếu qua user_did
 * và ánh xạ trực tiếp với Blockchain (where DID = ...).
 */
@Entity
@Table(
    name = "authorized_keys",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_did_pubkey",
            columnNames = {"user_did", "public_key_hex"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorizedKey {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * FK dùng user_did (VARCHAR) — ánh xạ trực tiếp với Blockchain.
     * ON DELETE CASCADE: xóa user → xóa hết authorized_keys.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_did", referencedColumnName = "user_did", nullable = false)
    private User user;

    /**
     * Public key hex của thiết bị phần cứng.
     * Unique theo cặp (user_did, public_key_hex).
     */
    @Column(name = "public_key_hex", length = 128, nullable = false)
    private String publicKeyHex;

    /**
     * Quyền hạn của khóa này.
     */
    @Column(name = "key_role", length = 50, nullable = false)
    @Builder.Default
    private String keyRole = "owner";

    /**
     * Locator ID trên mạng LampNet để tìm mảnh khóa.
     * Nullable — chỉ cần khi dùng LampNet storage (sau này).
     */
    @Column(name = "lampnet_locator_id", length = 128)
    private String lampnetLocatorId;

    /**
     * ZERO-TRUST: Chữ ký từ Root Key chứng minh việc cấp quyền này hợp lệ.
     *
     * NestJS (Backend) đã verify chữ ký trước khi gọi PK_DB.
     * PK_DB chỉ nhận và lưu — không verify được vì không có root public key.
     */
    @Column(name = "added_by_signature", length = 256, nullable = false)
    private String addedBySignature;

    /**
     * Trạng thái: active | revoked.
     * Revoked key không còn quyền ký gì trên hệ thống.
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

    public boolean isOwner() {
        return "owner".equals(keyRole);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
