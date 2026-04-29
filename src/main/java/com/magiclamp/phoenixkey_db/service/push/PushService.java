package com.magiclamp.phoenixkey_db.service.push;

/**
 * Gửi push notification đến mobile của user (FCM hoặc APNs).
 *
 * <p>Spec §15.5: payload push KHÔNG chứa data nhạy cảm — chỉ chứa request id.
 * Mobile nhận push → fetch chi tiết qua HTTPS authenticated.</p>
 *
 * <p>Hiện tại implementation là {@code PushServiceStub} — chỉ log, không gọi
 * FCM/APNs thật (cần Firebase project + APNs cert từ Long). Khi sẵn sàng, swap
 * sang {@code FirebasePushSender} + {@code ApnsPushSender}.</p>
 */
public interface PushService {

    /** Push tới tất cả device của user — báo có sign request mới. */
    void notifySignRequest(String userDid, String requestId);

    /** Push báo session approval (web → mobile shortcut, không cần QR). */
    void notifySessionApproval(String userDid, String sessionId);

    /** Push báo seed export request (intent special). */
    void notifySeedExportRequest(String userDid, String requestId);
}
