package com.magiclamp.phoenixkey_db.dto.session;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response cho {@code GET /auth/session/{id}/status}.
 *
 * <p>Web gọi sau khi SSE reconnect (network blip) để lấy state hiện tại — nếu
 * session đã approve trong lúc disconnect, web không phải đợi event tiếp theo.</p>
 *
 * <p>Khi {@code status = "approved"}: kèm {@code sessionToken} +
 * {@code linkedDeviceToken} (giống event SSE). Nếu pending/rejected/expired:
 * 2 trường này null.</p>
 *
 * @param sessionId          UUIDv7
 * @param status             pending | approved | rejected | expired
 * @param sessionToken       JWT session, chỉ trả khi approved
 * @param linkedDeviceToken  JWT linked-device, chỉ trả khi approved
 * @param userDid            chỉ trả khi approved
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionStatusResponse(
        String sessionId,
        String status,
        String sessionToken,
        String linkedDeviceToken,
        String userDid) {
}
