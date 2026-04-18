package com.magiclamp.phoenixkey_db.domain;

/**
 * [V1.5] Enum ánh xạ với PostgreSQL TYPE auth_provider.
 *
 * PhoenixKey không phụ thuộc Google/Apple.
 * Primary Auth = Biometric + Hardware Key tại chip thiết bị.
 *
 * Enum này phải khớp tuyệt đối với định nghĩa trong migration V1:
 *
 * CREATE TYPE auth_provider AS ENUM ('PHONE', 'EMAIL');
 *
 * Chỉ phục vụ 2 mục đích:
 * - Secondary Auth: Gửi OTP qua phone/email khi mất thiết bị tạm thời
 * - Discovery Bridge: Tìm DID qua SĐT/Email để invite Guardian
 */
public enum AuthProvider {
    PHONE,
    EMAIL
}
