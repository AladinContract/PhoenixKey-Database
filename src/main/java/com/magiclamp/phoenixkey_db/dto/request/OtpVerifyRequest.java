package com.magiclamp.phoenixkey_db.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO cho {@code POST /api/v1/auth/otp/verify}.
 *
 * App gọi endpoint này sau khi nhận OTP qua SMS/Email từ NestJS.
 * NestJS đã trả về {@code blind_hash} cho App trong luồng save OTP.
 *
 * PK_DB lookup Redis bằng {@code blind_hash} → so sánh OTP.
 * IP hash được log vào activity_logs.metadata để phục vụ rate limit theo IP.
 *
 * @see com.magiclamp.phoenixkey_db.dto.response.OtpVerifyResponse
 */
public record OtpVerifyRequest(
        @NotBlank(message = "Blind hash is required")
        String blindHash,

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        String otp,

        /** SHA-256 hash của IP nguồn — dùng log và rate limit. Không nhận IP thật. */
        @NotBlank(message = "IP hash is required for rate limiting")
        String ipHash) {
}
