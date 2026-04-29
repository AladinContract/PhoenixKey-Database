package com.magiclamp.phoenixkey_db.dto.session;

/**
 * Response cho mobile sau khi gọi {@code POST /auth/session/{id}/approve}.
 *
 * <p>Mobile lưu {@code linkedDeviceToken} cho lần đăng nhập sau — web có thể
 * trigger push qua endpoint {@code /auth/session/push} thay vì phải scan QR.</p>
 *
 * @param status            "approved"
 * @param linkedDeviceToken JWT linked-device, mobile lưu cho push notification later
 */
public record SessionApproveResponse(
        String status,
        String linkedDeviceToken) {
}
