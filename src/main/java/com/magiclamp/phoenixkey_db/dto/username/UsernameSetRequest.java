// ── UsernameSetRequest.java ──────────────────────────────────────────────────
// src/main/java/com/magiclamp/phoenixkey_db/dto/username/UsernameSetRequest.java

package com.magiclamp.phoenixkey_db.dto.username;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request đặt/đổi username.
 * Validation cơ bản tại đây; reserved-name check trong service.
 */
public record UsernameSetRequest(

        /**
         * 3–32 ký tự: chữ thường, số, dấu gạch dưới.
         * Không cho phép chữ hoa, khoảng trắng, ký tự đặc biệt.
         * Service tự lowercase trước khi lưu.
         */
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(
                regexp = "^[a-z0-9_]+$",
                message = "Username chỉ được chứa chữ thường, số và dấu gạch dưới (_)"
        )
        String username
) {}
