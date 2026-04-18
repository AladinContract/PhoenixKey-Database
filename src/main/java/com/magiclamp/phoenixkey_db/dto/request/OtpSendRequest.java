package com.magiclamp.phoenixkey_db.dto.request;

import com.magiclamp.phoenixkey_db.domain.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [V1.5] Request DTO cho {@code POST /api/v1/auth/otp/save}.
 *
 * NestJS gọi endpoint này sau khi:
 * - Nhận credential từ App
 * - Hash credential → blind_hash
 * - Generate OTP (6 chữ số)
 * - Gửi OTP qua SMS/Email
 * - Gọi PK_DB để lưu OTP vào Redis
 *
 * PK_DB nhận blind_hash + otp + credential + ipHash.
 * - `credential` dùng để re-hash khi pepper rotate.
 * - `ipHash` dùng để log và rate limit theo IP.
 *
 * [V1.5] Không còn GOOGLE/APPLE — chỉ phone + email.
 * Primary Auth = Biometric + Hardware Key tại chip thiết bị.
 * Secondary Auth = phone/email OTP.
 *
 * Zero-PII: Credential không lưu vào DB. IP thật không lưu — chỉ hash.
 */
public record OtpSendRequest(
        @NotBlank(message = "Blind hash is required")
        String blindHash,

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        String otp,

        @NotNull(message = "Provider is required")
        AuthProvider provider,

        /**
         * [V1.5] Credential gốc (email/phone) — dùng re-hash khi pepper rotate.
         * Validate pattern: hợp lệ nếu là email hoặc phone number.
         * Không lưu vào DB — chỉ dùng in-memory.
         */
        @NotBlank(message = "Credential is required for re-hash")
        @Pattern(
            regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$|^\\+?[0-9]{8,15}$",
            message = "Must be a valid email or phone number"
        )
        String credential,

        /** SHA-256 hash của IP nguồn — dùng log và rate limit. Không nhận IP thật. */
        @NotBlank(message = "IP hash is required for rate limiting")
        String ipHash) {
}