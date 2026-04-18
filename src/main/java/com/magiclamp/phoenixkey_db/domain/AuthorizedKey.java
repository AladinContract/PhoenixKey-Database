package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Quản lý đa khóa / đa thiết bị & định tuyến LampNet.
 *
 * Một User (DID) có thể có nhiều thiết bị/khóa:
 *   - owner: thiết bị gốc, toàn quyền
 *   - manager: ký giao dịch (thay vì farm_manager)
 *   - viewer: chỉ đọc, không ký (thay vì read_only)
 *
 * [V1.5] Thêm keyOrigin: biết nguồn gốc key để quyết định
 * có dùng LampNet khi Recovery hay không.
 *
 * ZERO-TRUST: {@code added_by_signature} là chữ ký từ Root Key.
 * NestJS (Backend) verify chữ ký này trước khi gọi PK_DB INSERT.
 * Nếu Backend bị hack, hacker không thể tự ý thêm khóa vì không có Root Key.
 * PK_DB chỉ nhận và lưu — không verify được vì không có root public key.
 *
 * [V1.5] Xóa lampnet_locator_id: LampNet topology thay đổi liên tục.
 * Locator = Hash(public_key_hex + SALT) tính on-the-fly.
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
     * Plain column thay vì @ManyToOne: Hibernate 6 không resolve non-PK FK
     * value khi insert. Queries theo DID string trực tiếp.
     */
    @Column(name = "user_did", length = 128, nullable = false, updatable = false)
    private String userDid;

    /**
     * Public key hex của thiết bị phần cứng.
     * Unique theo cặp (user_did, public_key_hex).
     */
    @Column(name = "public_key_hex", length = 256, nullable = false)
    private String publicKeyHex;

    /**
     * [V1.5] Nguồn gốc của Hardware Key.
     * SDK dùng để quyết định có tìm mảnh trên LampNet khi Recovery hay không.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "key_origin", nullable = false, columnDefinition = "key_origin_type")
    @Builder.Default
    private KeyOriginType keyOrigin = KeyOriginType.SECURE_ENCLAVE;

    /**
     * Quyền hạn của khóa này.
     * [V1.5] owner | manager | viewer (thay vì owner | farm_manager | read_only)
     */
    @Column(name = "key_role", length = 50, nullable = false)
    @Builder.Default
    private String keyRole = "owner";

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
