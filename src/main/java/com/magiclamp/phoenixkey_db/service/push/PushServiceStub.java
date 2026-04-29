package com.magiclamp.phoenixkey_db.service.push;

import com.magiclamp.phoenixkey_db.domain.DeviceToken;
import com.magiclamp.phoenixkey_db.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stub implementation — log thay vì gọi FCM/APNs thật.
 *
 * <p>Active mặc định ({@code @ConditionalOnMissingBean}) — sẽ được override bởi
 * {@code FirebasePushSender} khi {@code phoenixkey.push.fcm.enabled=true} +
 * {@code ApnsPushSender} khi {@code phoenixkey.push.apns.enabled=true}.</p>
 *
 * <p>Trong stub mode, mobile không nhận được push thật — phải poll
 * {@code GET /sign/request/{id}} hoặc dùng web QR scan flow.</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnMissingBean(value = PushService.class, ignored = PushServiceStub.class)
@Slf4j
public class PushServiceStub implements PushService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    public void notifySignRequest(String userDid, String requestId) {
        logStub("sign-request", userDid, requestId);
    }

    @Override
    public void notifySessionApproval(String userDid, String sessionId) {
        logStub("session-approval", userDid, sessionId);
    }

    @Override
    public void notifySeedExportRequest(String userDid, String requestId) {
        logStub("seed-export", userDid, requestId);
    }

    private void logStub(String type, String userDid, String correlationId) {
        List<DeviceToken> devices = deviceTokenRepository.findByUserDid(userDid);
        if (devices.isEmpty()) {
            log.warn("[push:STUB] {} userDid={} cid={} — no devices registered. "
                    + "Mobile phải poll endpoint thay vì đợi push.",
                    type, userDid, correlationId);
            return;
        }
        log.info("[push:STUB] {} userDid={} cid={} → would send to {} device(s). "
                + "Phase D.4 chưa wire FCM/APNs thật.",
                type, userDid, correlationId, devices.size());
    }
}
