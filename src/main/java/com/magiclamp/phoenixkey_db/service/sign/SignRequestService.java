package com.magiclamp.phoenixkey_db.service.sign;

import com.magiclamp.phoenixkey_db.dto.sign.SignApproveRequest;
import com.magiclamp.phoenixkey_db.dto.sign.SignRequestCreateRequest;
import com.magiclamp.phoenixkey_db.dto.sign.SignRequestCreateResponse;
import com.magiclamp.phoenixkey_db.dto.sign.SignRequestPayload;

/**
 * Sign request relay (spec §7).
 *
 * <p>Flow:
 * <ol>
 *   <li>Web: {@link #create(SignRequestCreateRequest, String)} với session_token Bearer</li>
 *   <li>Server: lưu Redis (TTL 120s), push notification mobile</li>
 *   <li>Mobile: {@link #get(String)} fetch chi tiết</li>
 *   <li>Mobile: user xem intent + biometric → ký → {@link #approve(String, SignApproveRequest)}</li>
 *   <li>Server: verify signature → push qua SSE cho web</li>
 *   <li>Web: nhận event {@code "signed"} với signature</li>
 * </ol>
 */
public interface SignRequestService {

    /** Web tạo sign request. Trả requestId + expiresAt. */
    SignRequestCreateResponse create(SignRequestCreateRequest request, String sessionToken);

    /** Mobile fetch chi tiết sign request — sau khi nhận push notification. */
    SignRequestPayload get(String requestId);

    /**
     * Mobile approve — verify signature → emit SSE event tới web.
     *
     * @param requestId UUIDv7
     * @param request   pubkey + signature DER
     */
    void approve(String requestId, SignApproveRequest request);

    /** Web cancel request đang chờ. */
    void cancel(String requestId, String sessionToken);
}
