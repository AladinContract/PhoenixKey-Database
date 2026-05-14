package com.magiclamp.phoenixkey_db.service.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.magiclamp.phoenixkey_db.domain.DeviceToken;
import com.magiclamp.phoenixkey_db.repository.DeviceTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Firebase Cloud Messaging push sender. Active when
 * {@code phoenixkey.push.fcm.enabled=true}.
 *
 * <p>Service account JSON loaded from Vault path
 * {@code secret/phoenixkey/push/fcm/service_account}.</p>
 *
 * <p>Spec §15.5: push payload MUST NOT contain the intent — only the
 * request_id. Mobile fetches details via authenticated HTTPS to avoid
 * leaking the intent through the notification service.</p>
 */
@Service
@ConditionalOnProperty(name = "phoenixkey.push.fcm.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class FirebasePushSender implements PushService {

    private final DeviceTokenRepository deviceTokenRepository;
    @Value("${FCM_SERVICE_ACCOUNT_JSON:}") private String fcmServiceAccountJson;

    @PostConstruct
    public void init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                String serviceAccountJson = fcmServiceAccountJson;
                GoogleCredentials creds = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))
                );
                FirebaseApp.initializeApp(FirebaseOptions.builder()
                        .setCredentials(creds)
                        .build());
                log.info("Firebase initialized for push notifications");
            }
        } catch (Exception e) {
            log.error("Firebase init failed: {}", e.getMessage());
            throw new RuntimeException("FCM init failed", e);
        }
    }

    @Override
    public void notifySignRequest(String userDid, String requestId) {
        send(userDid, "sign_request", Map.of("request_id", requestId));
    }

    @Override
    public void notifySessionApproval(String userDid, String sessionId) {
        send(userDid, "session_approval", Map.of("session_id", sessionId));
    }

    @Override
    public void notifySeedExportRequest(String userDid, String requestId) {
        send(userDid, "seed_export", Map.of("request_id", requestId));
    }

    @Override
    public void notifyActivationRequest(String genieDid, String activationId) {
        send(genieDid, "activation_request", Map.of("activation_id", activationId));
    }

    @Override
    public void notifyActivationPaymentReceived(String genieDid, String activationId) {
        send(genieDid, "activation_payment_received", Map.of("activation_id", activationId));
    }

    // ─── Internal ─────────────────────────────────────────────────

    private void send(String userDid, String eventType, Map<String, String> data) {
        var tokens = deviceTokenRepository.findByUserDid(userDid).stream()
                .filter(t -> "android".equalsIgnoreCase(t.getPlatform()))
                .filter(t -> t.getFcmToken() != null && !t.getFcmToken().isEmpty())
                .toList();
        if (tokens.isEmpty()) {
            log.debug("No Android FCM tokens for {} — skipping {}", userDid, eventType);
            return;
        }
        for (DeviceToken dt : tokens) {
            try {
                Message msg = Message.builder()
                        .setToken(dt.getFcmToken())
                        // Data-only message (no notification field) — keeps payload private
                        .putAllData(data)
                        .putData("event", eventType)
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .build())
                        .build();
                String msgId = FirebaseMessaging.getInstance().send(msg);
                log.info("FCM sent: user={}, event={}, msgId={}", userDid, eventType, msgId);
            } catch (Exception e) {
                log.warn("FCM send failed for {}: {}", dt.getFcmToken(), e.getMessage());
                // If error code is UNREGISTERED, delete stale token
                if (e.getMessage() != null && e.getMessage().contains("UNREGISTERED")) {
                    deviceTokenRepository.deleteByFcmToken(dt.getFcmToken());
                }
            }
        }
    }
}
