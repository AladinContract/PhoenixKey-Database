package com.magiclamp.phoenixkey_db.dto.sign;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Request cho {@code POST /seed/export-request} (spec §9.2).
 *
 * <p>Web trigger seed export → server tạo SignRequest đặc biệt
 * {@code intent.type=SEED_EXPORT} → mobile yêu cầu user biometric + xem cảnh báo
 * → mobile approve → server ghi {@code users.seed_exported_at = NOW()} +
 * log {@code seed_phrase_exported}.</p>
 *
 * @param sessionId   web's current session_id (để emit SSE result event)
 * @param displayText optional override cho intent.displayText (mobile hiển thị)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeedExportRequest(
        @NotBlank String sessionId,
        String displayText) {

    public String displayTextOrDefault() {
        return displayText != null && !displayText.isBlank()
                ? displayText
                : "Trích xuất Seed Phrase 24 từ — sau thao tác này, bảo mật tài khoản giảm cho đến khi xoay khóa.";
    }
}
