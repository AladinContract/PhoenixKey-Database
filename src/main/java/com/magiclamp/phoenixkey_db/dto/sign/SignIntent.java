package com.magiclamp.phoenixkey_db.dto.sign;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * PhoenixKey Signing Standard intent (spec §7.3).
 *
 * <p>Hiển thị trên màn hình mobile trước khi user ký — chống "blind signing".
 * User phải thấy rõ đang ký gì.</p>
 *
 * @param type        TRANSFER | SEED_EXPORT | KEY_ROTATE | CUSTOM
 * @param body        chi tiết payload (vd: {amount, to})
 * @param domain      web domain phát yêu cầu (vd "phoenixkey.me")
 * @param appId       app identifier (vd "phoenixkey-web-v1")
 * @param nonce       random nonce 32 byte hex (chống replay)
 * @param timestamp   epoch seconds
 * @param displayText human-readable summary cho user xem (vd "Chuyển 100 LAMP đến addr1q...")
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignIntent(
        @NotBlank String type,
        Map<String, Object> body,
        @NotBlank String domain,
        String appId,
        @NotBlank String nonce,
        long timestamp,
        @NotBlank String displayText) {
}
