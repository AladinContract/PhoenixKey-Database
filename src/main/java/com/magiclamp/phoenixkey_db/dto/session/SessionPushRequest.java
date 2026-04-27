package com.magiclamp.phoenixkey_db.dto.session;

import jakarta.validation.constraints.NotBlank;

/**
 * Request cho {@code POST /auth/session/push} (web → backend → mobile push).
 *
 * <p>Khi user đã từng đăng nhập trên web này, web có sẵn linked-device token
 * trong localStorage. Thay vì hiển thị QR mới, web có thể gọi endpoint này để
 * server gửi push notification về mobile đã liên kết → user phê duyệt trên
 * mobile mà không cần scan QR.</p>
 *
 * @param sessionId         session_id mới (web đã call /auth/session/init trước)
 * @param linkedDeviceToken JWT linked-device từ localStorage
 */
public record SessionPushRequest(
        @NotBlank String sessionId,
        @NotBlank String linkedDeviceToken) {
}
