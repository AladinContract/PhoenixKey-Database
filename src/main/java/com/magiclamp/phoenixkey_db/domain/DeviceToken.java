package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * FCM/APNs device token cho push notification (Phase D.4).
 *
 * <p>Một user có thể có nhiều token (đa thiết bị). Khi gửi push, scan tất cả
 * row của user_did → ưu tiên APNs trên iOS (stable hơn FCM-iOS proxy).</p>
 */
@Entity
@Table(name = "device_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_did", length = 128, nullable = false, updatable = false)
    private String userDid;

    /** {@code ios} | {@code android}. */
    @Column(name = "platform", length = 10, nullable = false)
    private String platform;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Column(name = "apns_token", length = 255)
    private String apnsToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
