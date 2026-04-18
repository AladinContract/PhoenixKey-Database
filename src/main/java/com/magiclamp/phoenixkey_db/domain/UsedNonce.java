package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * [V1.5] Bảng chống Replay Attack cho signing flow.
 *
 * signData() flow bắt buộc:
 *   1. Nhận request ký có kèm nonce
 *   2. Kiểm tra nonce trong used_nonces → Nếu tồn tại: REJECT
 *   3. Verify chữ ký với public_key_hex
 *   4. Thực thi logic nghiệp vụ
 *   5. Ghi nonce vào used_nonces với expires_at
 *
 * Tại sao PostgreSQL thay vì Redis cho nonce?
 * Redis TTL không đủ bảo đảm tính duy nhất tuyệt đối.
 * PostgreSQL với PRIMARY KEY composite (nonce, user_did) đảm bảo
 * không bao giờ có 2 nonce trùng nhau cho cùng 1 user.
 *
 * Cleanup: Xóa nonce hết hạn mỗi giờ.
 * Cronjob: DELETE FROM used_nonces WHERE expires_at < NOW();
 */
@Entity
@Table(name = "used_nonces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(UsedNonceId.class)
public class UsedNonce {

    @Id
    @Column(name = "nonce", nullable = false, updatable = false, length = 64)
    private String nonce;

    @Id
    @Column(name = "user_did", nullable = false, updatable = false, length = 128)
    private String userDid;

    @Column(name = "used_at", nullable = false, updatable = false)
    private OffsetDateTime usedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
}