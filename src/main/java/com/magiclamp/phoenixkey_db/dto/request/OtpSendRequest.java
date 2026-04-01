package com.magiclamp.phoenixkey_db.dto.request;

import com.magiclamp.phoenixkey_db.domain.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO cho {@code POST /api/v1/auth/otp/send}.
 *
 * <p>
 * App gửi credential plaintext. PK_DB tự hash bên trong với Pepper.
 *
 * @see com.magiclamp.phoenixkey_db.dto.response.OtpSendResponse
 */
public record OtpSendRequest(
        @NotBlank(message = "Credential is required") String credential,

        @NotNull(message = "Provider is required") AuthProvider provider) {
}
