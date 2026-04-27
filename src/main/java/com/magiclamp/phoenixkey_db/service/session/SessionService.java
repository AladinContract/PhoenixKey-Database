package com.magiclamp.phoenixkey_db.service.session;

import com.magiclamp.phoenixkey_db.dto.session.SessionApproveRequest;
import com.magiclamp.phoenixkey_db.dto.session.SessionApproveResponse;
import com.magiclamp.phoenixkey_db.dto.session.SessionInitResponse;
import com.magiclamp.phoenixkey_db.dto.session.SessionStatusResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * QR-pairing session flow giữa web ↔ mobile (spec §6).
 *
 * <p>Flow:
 * <ol>
 *   <li>Web: {@link #init()} → nhận {sessionId, challenge, tempToken}, hiển thị QR</li>
 *   <li>Web: {@link #openStream(String, String)} → SSE đăng ký vào registry</li>
 *   <li>Mobile: quét QR → ký challenge → {@link #approveByMobile(String, SessionApproveRequest)}</li>
 *   <li>Server: verify signature, mint session_token + linked_device_token, push qua SSE</li>
 *   <li>Web: nhận event "approved" → vào dashboard</li>
 * </ol>
 */
public interface SessionService {

    /** Tạo session mới với challenge ngẫu nhiên. TTL 5 phút (spec §6.3). */
    SessionInitResponse init();

    /**
     * Mở SSE stream cho web client. {@code tempToken} phải bind với sessionId
     * — chống ai khác listen session của user.
     */
    SseEmitter openStream(String sessionId, String tempToken);

    /** Status hiện tại của session (web fallback khi SSE reconnect). */
    SessionStatusResponse getStatus(String sessionId, String tempToken);

    /**
     * Mobile approve session sau khi user xác thực biometric. Verify signature
     * → mint session_token + linked_device_token → push qua SSE cho web.
     */
    SessionApproveResponse approveByMobile(String sessionId, SessionApproveRequest request);

    /**
     * Gửi push notification về mobile đã liên kết. Web có sẵn linkedDeviceToken
     * trong localStorage → trigger push thay vì hiển thị QR mới.
     *
     * @param sessionId         session_id mới (web đã call init trước)
     * @param linkedDeviceToken JWT linked-device từ localStorage
     */
    void pushToLinkedDevice(String sessionId, String linkedDeviceToken);
}
