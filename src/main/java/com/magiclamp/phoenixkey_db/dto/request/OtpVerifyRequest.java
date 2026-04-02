package com.magiclamp.phoenixkey_db.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO cho {@code POST /api/v1/auth/otp/verify}.
 *
 * <p>
 * App gọi endpoint này sau khi nhận OTP qua SMS/Email từ NestJS.
 * NestJS đã trả về {@code blind_hash} cho App trong luồng save OTP.
 *
 * <p>
 * PK_DB lookup Redis bằng {@code blind_hash} → so sánh OTP.
 *
 * @see com.magiclamp.phoenixkey_db.dto.response.OtpVerifyResponse
 */
public record OtpVerifyRequest(
        @NotBlank(message = "Blind hash is required") String blindHash,

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be 6 digits") String otp) {
}
