package com.magiclamp.phoenixkey_db.dto.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Mobile gọi {@code POST /devices/register} sau khi login để lưu push token.
 *
 * <p>Server lookup userDid từ Bearer session_token. Mobile chỉ cần gửi platform
 * + token (FCM hoặc APNs hoặc cả 2).</p>
 *
 * @param platform  ios | android
 * @param fcmToken  FCM token (Android + iOS qua FCM gateway). Tuỳ chọn.
 * @param apnsToken APNs device token (iOS native, ưu tiên trên iOS). Tuỳ chọn.
 */
public record DeviceRegisterRequest(
        @NotBlank @Pattern(regexp = "^(ios|android)$", message = "platform phải là 'ios' hoặc 'android'")
        String platform,

        String fcmToken,
        String apnsToken) {

    public boolean hasAnyToken() {
        return (fcmToken != null && !fcmToken.isBlank())
                || (apnsToken != null && !apnsToken.isBlank());
    }
}
