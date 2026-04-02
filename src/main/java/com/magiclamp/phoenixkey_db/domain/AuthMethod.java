package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ánh xạ Web2 Auth → DID sử dụng Blind Index.
 *
 * Thay vì lưu SĐT/Email plaintext (rủi ro bảo mật), hệ thống lưu
 * HMAC-SHA256(phone_or_email, SERVER_PEPPER) — gọi là Blind Index Hash.
 * Nếu DB bị hack, hacker chỉ nhận được hash vô nghĩa.
 *
 * Luồng đăng nhập:
 * 
 * 1. User nhập email/phone
 * 2. Backend hash(input) với Pepper từ Vault → blindHash
 * 3. SELECT * FROM auth_methods WHERE blind_index_hash = :blindHash
 * 4. Trả về user_did → tạo session
 *
 * Pepper Rotation: Khi SERVER_PEPPER xoay vòng trên Vault (6 tháng/lần),
 * pepper_version tăng lên. Hệ thống phải re-hash tất cả credentials
 * của user đó ở lần đăng nhập tiếp theo.
 *
 */
@Entity
@Table(name = "auth_methods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthMethod {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Liên kết ngược về User.
     * ON DELETE CASCADE: xóa user → xóa hết auth_methods.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Nhà cung cấp xác thực.
     * Ánh xạ sang PostgreSQL ENUM 'auth_provider'.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, columnDefinition = "auth_provider")
    private AuthProvider provider;

    /**
     * Blind Index Hash: HMAC-SHA256(phone_or_email, SERVER_PEPPER).
     *
     * 
     * Unique — không có 2 auth_methods cùng blind_hash.
     * Index trên cột này để tra cứu nhanh khi đăng nhập.
     *
     * 
     * Lưu ý: hash ở dạng hex string (64 ký tự), không phải binary.
     */
    @Column(name = "blind_index_hash", length = 64, nullable = false, unique = true)
    private String blindIndexHash;

    /**
     * Phiên bản Pepper dùng để tạo hash này.
     *
     * 
     * Phục vụ Pepper Rotation:
     * - Khi rotate Pepper → tăng pepper_version lên 2, 3...
     * - Lần đăng nhập tiếp theo, hệ thống re-hash với pepper mới
     * - pepper_version cũ vẫn verify được (multi-version support)
     */
    @Column(name = "pepper_version", nullable = false)
    @Builder.Default
    private Integer pepperVersion = 1;

    /**
     * Trạng thái xác thực: đã xác minh (OTP verify) hay chưa.
     *
     * 
     * Luồng đăng ký:
     * 
     * 1. User gửi email/SĐT → is_verified = false
     * 2. Backend gửi OTP → user nhập OTP
     * 3. Backend set is_verified = true
     * 
     */
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    /**
     * Thời điểm auth method được thêm vào.
     */
    @Column(name = "added_at", nullable = false, updatable = false)
    private OffsetDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = OffsetDateTime.now();
        }
    }
}
