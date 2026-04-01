package com.magiclamp.phoenixkey_db.dto.request;

import com.magiclamp.phoenixkey_db.domain.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO cho {@code POST /api/v1/auth/otp/verify}.
 *
 * <p>
 * App gửi credential plaintext + OTP. PK_DB:
 * <ol>
 * <li>Hash credential → blind_hash</li>
 * <li>Tra Redis: so sánh OTP</li>
 * <li>Đúng → set is_verified = true</li>
 * <li>Trả về user_did + blind_hash</li>
 * </ol>
 *
 * @see com.magiclamp.phoenixkey_db.dto.response.OtpVerifyResponse
 */
public record OtpVerifyRequest(
        @NotBlank(message = "Credential is required") String credential,

        @NotNull(message = "Provider is required") AuthProvider provider,

        @NotBlank(message = "OTP is required") @Size(min = 6, max = 6, message = "OTP must be 6 digits") String otp) {
}
