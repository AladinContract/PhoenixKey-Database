package com.magiclamp.phoenixkey_db.dto.sign;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Web tạo sign request — gọi {@code POST /sign/request}.
 *
 * <p>Request kèm session token (Bearer) để xác định user. Server lookup userDid
 * từ token → tìm device đã liên kết → push notification → mobile fetch payload.</p>
 *
 * @param sessionId session_id của web hiện tại (để emit SSE event approval về cùng SSE channel)
 * @param intent    payload cần ký (đầy đủ Sign Intent shape)
 */
public record SignRequestCreateRequest(
        @NotBlank String sessionId,
        @NotNull @Valid SignIntent intent) {
}
