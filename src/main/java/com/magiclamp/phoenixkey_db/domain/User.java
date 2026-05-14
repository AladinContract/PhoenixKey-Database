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
 * PhoenixKey-Server KHÔNG lưu PII (tên, email, SĐT) — flow mới chỉ Hardware Key
 * trong Secure Enclave mobile + biometric.
 *
 * Nguyên tắc:
 * - id = UUIDv7 do server tạo (timestamp-prefixed, tốt cho B-Tree insert)
 * - user_did = DID string bất biến suốt vòng đời user (không đổi kể cả khi xoay
 *   khóa). Format: {@code did:cardano:<network>:<txHash>}
 * - username = lookup shortcut tùy chọn — KHÔNG thay thế DID là anchor định danh.
 *   Username chỉ dùng để tìm DID nhanh, không dùng làm auth credential.
 * - {@code seed_exported_at} đánh dấu user đã trích xuất Seed Phrase (spec §9.5)
 *   — dashboard banner cảnh báo health thấp đến khi user thực hiện Key Rotation.
 *
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
     * Server tạo trước khi INSERT, KHÔNG dùng gen_random_uuid() của PostgreSQL.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * DID string từ Blockchain ({@code did:cardano:<network>:<txHash>}).
     * Bất biến suốt vòng đời — không thay đổi kể cả khi xoay khóa (key rotation).
     * Tài sản số gắn với DID này, không gắn với key.
     */
    @Column(name = "user_did", length = 128, nullable = false, unique = true, updatable = false)
    private String userDid;

    /**
     * [V1.5] Optimistic Locking cho đa thiết bị.
     * JPA tự động tăng version mỗi khi entity được merged.
     * Chống xung đột ghi đồng thời trên đa thiết bị.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * [Spec §9.5] Thời điểm user trích xuất Seed Phrase qua flow {@code SEED_EXPORT}.
     *
     * Khi {@code != null}, dashboard hiển thị banner cảnh báo bảo mật:
     * - 0–72h: cảnh báo vàng
     * - >72h: cảnh báo đỏ (không tắt được, chỉ Key Rotation mới reset)
     *
     * Sau khi Key Rotation thành công → reset về null.
     */
    @Column(name = "seed_exported_at")
    private OffsetDateTime seedExportedAt;

    // ──────────────────────────────────────────────────────────────
    // [V10] Username — lookup shortcut tùy chọn
    // ──────────────────────────────────────────────────────────────

    /**
     * Username tùy chọn — 3–32 ký tự [a-z0-9_], lưu lowercase.
     *
     * KHÔNG là auth credential — chỉ là lookup shortcut để tìm DID.
     * Flow đăng nhập bằng username:
     *   1. Web: GET /identity/by-username/{username} → nhận DID
     *   2. Web: init session với DID đó → hiển thị QR
     *   3. Mobile: approve QR bằng Hardware Key (biometric)
     *
     * Cooldown 30 ngày sau khi đặt (enforce tại service layer).
     * Reserved names (admin, system, ...) enforce tại service layer.
     */
    @Column(name = "username", length = 32, unique = true)
    private String username;

    /**
     * Thời điểm đặt username lần cuối.
     * Dùng để enforce 30-day cooldown đổi username.
     */
    @Column(name = "username_set_at")
    private OffsetDateTime usernameSetAt;

    // ──────────────────────────────────────────────────────────────
    // Relations
    // ──────────────────────────────────────────────────────────────

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
