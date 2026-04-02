package com.magiclamp.phoenixkey_db.dto.request;

import com.magiclamp.phoenixkey_db.domain.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO cho {@code POST /api/v1/auth/otp/save}.
 *
 * NestJS gọi endpoint này sau khi:
 * - Nhận credential từ App
 * - Hash credential → blind_hash
 * - Generate OTP (6 chữ số)
 * - Gửi OTP qua SMS/Email
 * - Gọi PK_DB để lưu OTP vào Redis
 *
 * PK_DB chỉ nhận blind_hash + otp. Không biết email/phone thật (Zero-PII).
 *
 * @see com.magiclamp.phoenixkey_db.dto.response.OtpSendResponse
 */
public record OtpSendRequest(
        @NotBlank(message = "Blind hash is required") String blindHash,

        @NotBlank(message = "OTP is required") @Size(min = 6, max = 6, message = "OTP must be 6 digits") String otp,

        @NotNull(message = "Provider is required") AuthProvider provider) {
}
