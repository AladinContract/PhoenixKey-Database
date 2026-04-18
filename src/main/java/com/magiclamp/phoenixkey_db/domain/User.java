package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lõi Định danh — mỗi dòng = một DID duy nhất trên Blockchain Cardano.
 *
 * PhoenixKey Database KHÔNG lưu PII (tên, email, SĐT) ở đây.
 * Mọi ánh xạ DID ↔ Web2 credentials nằm trong {@link AuthMethod}.
 *
 * Nguyên tắc:
 * - id = UUIDv7 do Backend tạo (timestamp-prefixed, tốt cho B-Tree insert)
 * - user_did = DID string bất biến suốt vòng đời user (không đổi kể cả khi key
 * rotation)
 * - Tài sản (Job, Jem, NFT) gắn với user_did, không gắn với key
 *
 * @see AuthMethod
 * @see AuthorizedKey
 * @see Guardian
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * UUIDv7 — timestamp-prefixed.
     * Backend tạo trước khi INSERT, KHÔNG dùng gen_random_uuid() của PostgreSQL.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * DID string từ Blockchain (did:prism:... hoặc did:cardano:...).
     * Bất biến suốt vòng đời — không thay đổi kể cả khi xoay khóa (key rotation).
     * Tài sản số gắn với DID này, không gắn với key.
     */
    @Column(name = "user_did", length = 128, nullable = false, unique = true, updatable = false)
    private String userDid;

    /**
     * [V1.5] Optimistic Locking cho đa thiết bị.
     * JPA tự động tăng version mỗi khi entity được merged.
     * Chống xung đột ghi đồng thời trên đa thiết bị.
     *
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ──────────────────────────────────────────────────────────────
    // Relations
    // ──────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AuthMethod> authMethods = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Guardian> guardians = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ActivityLog> activityLogs = new ArrayList<>();

    // ──────────────────────────────────────────────────────────────
    // Lifecycle callbacks
    // ──────────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (version == null) {
            version = 0L;
        }
    }
}
