package com.magiclamp.phoenixkey_db.service.push;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import com.magiclamp.phoenixkey_db.domain.DeviceToken;
import com.magiclamp.phoenixkey_db.repository.DeviceTokenRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Apple Push Notification Service sender via Pushy.
 * Active when {@code phoenixkey.push.apns.enabled=true}.
 *
 * <p>Uses token-based authentication (.p8 key from Vault).</p>
 */
@Service
@ConditionalOnProperty(name = "phoenixkey.push.apns.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ApnsPushSender implements PushService {

    private final DeviceTokenRepository deviceTokenRepository;
    @Value("${APNS_P8_KEY:}") private String apnsP8Key;

    @Value("${phoenixkey.push.apns.team-id}")
    private String teamId;

    @Value("${phoenixkey.push.apns.key-id}")
    private String keyId;

    @Value("${phoenixkey.push.apns.bundle-id}")
    private String bundleId;

    @Value("${phoenixkey.push.apns.production:false}")
    private boolean production;

    private ApnsClient apnsClient;

    @PostConstruct
    public void init() {
        try {
            String p8Key = apnsP8Key;
            apnsClient = new ApnsClientBuilder()
                    .setApnsServer(production
                            ? ApnsClientBuilder.PRODUCTION_APNS_HOST
                            : ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
                    .setSigningKey(ApnsSigningKey.loadFromInputStream(
                            new ByteArrayInputStream(p8Key.getBytes(StandardCharsets.UTF_8)),
                            teamId, keyId))
                    .build();
            log.info("APNs client initialized (production={})", production);
        } catch (Exception e) {
            log.error("APNs init failed: {}", e.getMessage());
            throw new RuntimeException("APNs init failed", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (apnsClient != null) {
            apnsClient.close();
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
                .filter(t -> "ios".equalsIgnoreCase(t.getPlatform()))
                .filter(t -> t.getApnsToken() != null && !t.getApnsToken().isEmpty())
                .toList();
        if (tokens.isEmpty()) {
            log.debug("No iOS APNs tokens for {} — skipping {}", userDid, eventType);
            return;
        }

        // Background data-only payload (content-available=1, no alert).
        // NOTE: setContentAvailable is declared on base ApnsPayloadBuilder and returns the base
        // type — chaining it on `new SimpleApnsPayloadBuilder()` would force the local var to
        // be `ApnsPayloadBuilder` and lose access to addCustomProperty. Keep statements split.
        SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
        payloadBuilder.setContentAvailable(true);
        // Pushy doesn't have a direct "custom property" putAll — set individually
        data.forEach((k, v) -> payloadBuilder.addCustomProperty(k, v));
        payloadBuilder.addCustomProperty("event", eventType);

        String payload = payloadBuilder.build();

        for (DeviceToken dt : tokens) {
            try {
                String token = TokenUtil.sanitizeTokenString(dt.getApnsToken());
                SimpleApnsPushNotification notification = new SimpleApnsPushNotification(
                        token, bundleId, payload);
                PushNotificationResponse<SimpleApnsPushNotification> response =
                        apnsClient.sendNotification(notification).get();
                if (response.isAccepted()) {
                    log.info("APNs accepted: user={}, event={}", userDid, eventType);
                } else {
                    log.warn("APNs rejected: {}", response.getRejectionReason());
                    if ("Unregistered".equals(response.getRejectionReason().orElse(""))) {
                        deviceTokenRepository.deleteByApnsToken(dt.getApnsToken());
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                log.warn("APNs send failed for {}: {}", dt.getApnsToken(), e.getMessage());
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        }
    }
}
