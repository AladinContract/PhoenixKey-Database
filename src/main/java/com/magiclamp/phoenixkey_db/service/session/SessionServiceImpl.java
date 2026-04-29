package com.magiclamp.phoenixkey_db.service.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magiclamp.phoenixkey_db.common.UuidGenerator;
import com.magiclamp.phoenixkey_db.dto.session.SessionApproveRequest;
import com.magiclamp.phoenixkey_db.dto.session.SessionApproveResponse;
import com.magiclamp.phoenixkey_db.dto.session.SessionInitResponse;
import com.magiclamp.phoenixkey_db.dto.session.SessionStatusResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.domain.User;
import com.magiclamp.phoenixkey_db.repository.AuthorizedKeyRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import com.magiclamp.phoenixkey_db.security.JwtService;
import com.magiclamp.phoenixkey_db.security.JwtServiceImpl;
import com.magiclamp.phoenixkey_db.service.activity.ActivityLogService;
import com.magiclamp.phoenixkey_db.service.redis.RedisService;
import com.magiclamp.phoenixkey_db.service.crypto.SignatureService;
import com.magiclamp.phoenixkey_db.service.push.PushService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionServiceImpl implements SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 phút — proxy enterprise có thể kill sớm hơn

    /** Cho phép timestamp lệch tối đa 60s so với server (chống replay tx cũ). */
    private static final long TIMESTAMP_SKEW_SEC = 60;

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";

    private static final String EVENT_APPROVED = "approved";

    private final RedisService redisService;
    private final JwtService jwtService;
    private final SignatureService signatureService;
    private final SseEmitterRegistry sseRegistry;
    private final UuidGenerator uuidGenerator;
    private final AuthorizedKeyRepository authorizedKeyRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final PushService pushService;
    private final ObjectMapper objectMapper;

    @Value("${phoenixkey.challenge.ttl-seconds:300}")
    private int challengeTtlSeconds;

    @Value("${phoenixkey.session.ttl-seconds:86400}")
    private int sessionTtlSeconds;

    // ──────────────────────────────────────────────────────────────
    // init
    // ──────────────────────────────────────────────────────────────

    @Override
    public SessionInitResponse init() {
        String sessionId = uuidGenerator.create().toString();
        String challenge = randomHex(32);
        Duration ttl = Duration.ofSeconds(challengeTtlSeconds);
        long expiresAt = Instant.now().plus(ttl).getEpochSecond();
        String tempToken = jwtService.mintTempToken(sessionId, ttl);

        // Lưu state vào Redis. Status pending → mobile chưa approve.
        SessionInitState state = new SessionInitState(challenge, STATUS_PENDING, expiresAt);
        redisService.saveSessionInit(sessionId, toJson(state), ttl);

        log.info("Session init: id={}, expiresAt={}", sessionId, expiresAt);
        return new SessionInitResponse(sessionId, challenge, tempToken, expiresAt);
    }

    // ──────────────────────────────────────────────────────────────
    // openStream + status
    // ──────────────────────────────────────────────────────────────

    @Override
    public SseEmitter openStream(String sessionId, String tempToken) {
        verifyTempToken(sessionId, tempToken);
        return sseRegistry.register(sessionId, SSE_TIMEOUT_MS);
    }

    @Override
    public SessionStatusResponse getStatus(String sessionId, String tempToken) {
        verifyTempToken(sessionId, tempToken);

        // Check approved state trước (24h TTL, lâu hơn init)
        var approvedJson = redisService.getSessionApproved(sessionId);
        if (approvedJson.isPresent()) {
            SessionApprovedState approved = fromJson(approvedJson.get(), SessionApprovedState.class);
            return new SessionStatusResponse(
                    sessionId, STATUS_APPROVED,
                    approved.sessionToken(), approved.linkedDeviceToken(), approved.userDid());
        }

        // Còn trong giai đoạn init — pending hoặc expired (TTL hết)
        var initJson = redisService.getSessionInit(sessionId);
        if (initJson.isEmpty()) {
            return new SessionStatusResponse(sessionId, "expired", null, null, null);
        }
        SessionInitState state = fromJson(initJson.get(), SessionInitState.class);
        return new SessionStatusResponse(sessionId, state.status(), null, null, null);
    }

    // ──────────────────────────────────────────────────────────────
    // approveByMobile
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SessionApproveResponse approveByMobile(String sessionId, SessionApproveRequest request) {
        // 1. Load init state — phải còn pending
        SessionInitState state = loadInitState(sessionId);
        if (!STATUS_PENDING.equals(state.status())) {
            throw new AppException(ErrorCode.SESSION_ALREADY_APPROVED,
                    "session not in pending state: " + state.status());
        }

        // 2. Validate timestamp (chống replay attack với challenge cũ)
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - request.timestamp()) > TIMESTAMP_SKEW_SEC) {
            log.warn("Session approve: timestamp skew too large, now={}, req={}", now, request.timestamp());
            throw new AppException(ErrorCode.SIGNATURE_INVALID,
                    "timestamp skew exceeds " + TIMESTAMP_SKEW_SEC + "s");
        }

        // 3. Verify pubkey thuộc về userDid (khớp authorized_keys active)
        boolean keyOk = authorizedKeyRepository.existsByUserDidAndPublicKeyHexAndStatus(
                request.userDid(), request.publicKeyHex(), "active");
        if (!keyOk) {
            log.warn("Session approve: pubkey not authorized for userDid={}", request.userDid());
            throw new AppException(ErrorCode.SIGNATURE_INVALID,
                    "publicKeyHex không thuộc về userDid này (không có active authorized_keys row)");
        }

        // 4. Verify signature trên (challenge + domain + timestamp)
        String message = state.challenge() + ":" + request.domain() + ":" + request.timestamp();
        byte[] sigBytes = hexToBytes(request.signature());
        if (!signatureService.verifyEcdsa(request.publicKeyHex(),
                message.getBytes(StandardCharsets.UTF_8), sigBytes)) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID,
                    "Challenge signature verification failed");
        }

        // 5. Mint tokens
        String sessionToken = jwtService.mintSessionToken(request.userDid(),
                Duration.ofSeconds(sessionTtlSeconds));
        String linkedDeviceToken = jwtService.mintLinkedDeviceToken(request.userDid());

        // 6. Save approved state + linked-device mapping
        SessionApprovedState approved = new SessionApprovedState(
                request.userDid(), sessionToken, linkedDeviceToken);
        redisService.saveSessionApproved(sessionId, toJson(approved),
                Duration.ofSeconds(sessionTtlSeconds));
        redisService.saveLinkedDevice(linkedDeviceToken, request.userDid(), Duration.ofDays(30));

        // 7. Push SSE event tới web client
        Map<String, Object> event = Map.of(
                "status", STATUS_APPROVED,
                "sessionToken", sessionToken,
                "linkedDeviceToken", linkedDeviceToken,
                "userDid", request.userDid());
        boolean pushed = sseRegistry.emit(sessionId, EVENT_APPROVED, event);
        log.info("Session approved: sid={}, userDid={}, ssePushed={}",
                sessionId, request.userDid(), pushed);

        // 8. Activity log với userId từ users table (spec §10 — audit phải có
        //    user reference để cursor-paginated /activity-logs filter đúng).
        //    Soft fallback null nếu user record không tồn tại (DB inconsistent).
        try {
            UUID userId = userRepository.findByUserDid(request.userDid())
                    .map(User::getId).orElse(null);
            activityLogService.log(userId, ActivityLogService.ACTION_WEB_SESSION_APPROVED,
                    Map.of("user_did", request.userDid(), "session_id", sessionId));
        } catch (Exception e) {
            log.warn("activity log failed: {}", e.getMessage());
        }

        return new SessionApproveResponse(STATUS_APPROVED, linkedDeviceToken);
    }

    // ──────────────────────────────────────────────────────────────
    // pushToLinkedDevice (stub — sẽ wire FCM/APNs ở Phase D.4)
    // ──────────────────────────────────────────────────────────────

    @Override
    public void pushToLinkedDevice(String sessionId, String linkedDeviceToken) {
        // Validate linked-device token + session tồn tại.
        Claims claims = jwtService.parseAndVerify(linkedDeviceToken);
        if (!JwtServiceImpl.TYPE_LINKED_DEVICE.equals(claims.get("type", String.class))) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "not a linked-device token");
        }
        String userDid = claims.getSubject();

        // Verify session vẫn pending (web đã init trước khi gọi push).
        loadInitState(sessionId); // throws nếu không tồn tại

        pushService.notifySessionApproval(userDid, sessionId);
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private void verifyTempToken(String sessionId, String tempToken) {
        Claims claims = jwtService.parseAndVerify(tempToken);
        if (!JwtServiceImpl.TYPE_TEMP.equals(claims.get("type", String.class))
                || !sessionId.equals(claims.getSubject())) {
            throw new AppException(ErrorCode.SESSION_NOT_FOUND,
                    "tempToken không khớp sessionId");
        }
    }

    private SessionInitState loadInitState(String sessionId) {
        return redisService.getSessionInit(sessionId)
                .map(json -> fromJson(json, SessionInitState.class))
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND,
                        "session: " + sessionId));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.SYSTEM_INTERNAL_ERROR, "JSON serialize failed: " + e.getMessage());
        }
    }

    private <T> T fromJson(String json, Class<T> cls) {
        try {
            return objectMapper.readValue(json, cls);
        } catch (Exception e) {
            throw new AppException(ErrorCode.SYSTEM_INTERNAL_ERROR, "JSON parse failed: " + e.getMessage());
        }
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "signature hex is null");
        }
        String s = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if (s.length() % 2 != 0) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "hex length must be even");
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            try {
                out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
            } catch (NumberFormatException e) {
                throw new AppException(ErrorCode.SIGNATURE_INVALID, "non-hex char at " + (i * 2));
            }
        }
        return out;
    }

    // Internal Redis JSON shapes — package-private records.
    record SessionInitState(String challenge, String status, long expiresAt) {
    }

    record SessionApprovedState(String userDid, String sessionToken, String linkedDeviceToken) {
    }
}
