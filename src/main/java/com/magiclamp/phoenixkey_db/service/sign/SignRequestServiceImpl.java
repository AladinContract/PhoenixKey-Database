package com.magiclamp.phoenixkey_db.service.sign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.magiclamp.phoenixkey_db.common.UuidGenerator;
import com.magiclamp.phoenixkey_db.domain.User;
import com.magiclamp.phoenixkey_db.dto.sign.SignApproveRequest;
import com.magiclamp.phoenixkey_db.dto.sign.SignIntent;
import com.magiclamp.phoenixkey_db.dto.sign.SignRequestCreateRequest;
import com.magiclamp.phoenixkey_db.dto.sign.SignRequestCreateResponse;
import com.magiclamp.phoenixkey_db.dto.sign.SignRequestPayload;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.AuthorizedKeyRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import com.magiclamp.phoenixkey_db.security.JwtService;
import com.magiclamp.phoenixkey_db.security.JwtServiceImpl;
import com.magiclamp.phoenixkey_db.service.activity.ActivityLogService;
import com.magiclamp.phoenixkey_db.service.nonce.NonceService;
import com.magiclamp.phoenixkey_db.service.redis.RedisService;
import com.magiclamp.phoenixkey_db.service.crypto.SignatureService;
import com.magiclamp.phoenixkey_db.service.push.PushService;
import com.magiclamp.phoenixkey_db.service.session.SseEmitterRegistry;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class SignRequestServiceImpl implements SignRequestService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_CANCELLED = "cancelled";

    private static final String EVENT_SIGNED = "signed";
    private static final String EVENT_CANCELLED = "cancelled";

    /** Spec §15.2 — intent.timestamp lệch tối đa 60s so với server (chống replay). */
    private static final long TIMESTAMP_SKEW_SEC = 60;

    private static final String META_USER_DID = "user_did";
    private static final String META_REQUEST_ID = "request_id";
    private static final String META_INTENT_TYPE = "intent_type";
    private static final String INTENT_SEED_EXPORT = "SEED_EXPORT";

    private final RedisService redisService;
    private final JwtService jwtService;
    private final SignatureService signatureService;
    private final SseEmitterRegistry sseRegistry;
    private final UuidGenerator uuidGenerator;
    private final AuthorizedKeyRepository authorizedKeyRepository;
    private final UserRepository userRepository;
    private final NonceService nonceService;
    private final ActivityLogService activityLogService;
    private final PushService pushService;

    /**
     * Canonical ObjectMapper — keys sorted alphabetically, no indent. Mobile và
     * server phải dùng cùng canonicalization để hash trùng signature input.
     */
    private final ObjectMapper canonicalMapper;

    @Value("${phoenixkey.sign-request.ttl-seconds:120}")
    private int signRequestTtlSeconds;

    @SuppressWarnings("java:S107") // 11 deps — service orchestrate nhiều layer là expected
    public SignRequestServiceImpl(
            RedisService redisService,
            JwtService jwtService,
            SignatureService signatureService,
            SseEmitterRegistry sseRegistry,
            UuidGenerator uuidGenerator,
            AuthorizedKeyRepository authorizedKeyRepository,
            UserRepository userRepository,
            NonceService nonceService,
            ActivityLogService activityLogService,
            PushService pushService,
            ObjectMapper objectMapper) {
        this.redisService = redisService;
        this.jwtService = jwtService;
        this.signatureService = signatureService;
        this.sseRegistry = sseRegistry;
        this.uuidGenerator = uuidGenerator;
        this.authorizedKeyRepository = authorizedKeyRepository;
        this.userRepository = userRepository;
        this.nonceService = nonceService;
        this.activityLogService = activityLogService;
        this.pushService = pushService;
        // Copy ObjectMapper rồi enable sort keys — không ảnh hưởng global mapper.
        this.canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    // ──────────────────────────────────────────────────────────────
    // create
    // ──────────────────────────────────────────────────────────────

    @Override
    public SignRequestCreateResponse create(SignRequestCreateRequest request, String sessionToken) {
        Claims claims = jwtService.parseAndVerify(sessionToken);
        if (!JwtServiceImpl.TYPE_SESSION.equals(claims.get("type", String.class))) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "not a session token");
        }
        String userDid = claims.getSubject();

        String requestId = uuidGenerator.create().toString();
        Duration ttl = Duration.ofSeconds(signRequestTtlSeconds);
        long expiresAt = Instant.now().plus(ttl).getEpochSecond();

        SignRequestPayload payload = new SignRequestPayload(
                requestId, userDid, request.sessionId(),
                request.intent(), STATUS_PENDING, expiresAt);

        redisService.saveSignRequest(requestId, toJson(payload), ttl);

        // Spec §10 audit trail — log mọi sign-request init (đặc biệt quan trọng
        // với SEED_EXPORT). User lookup soft fail vì userDid đã verify qua JWT.
        UUID userId = lookupUserId(userDid);
        activityLogService.log(userId, ActivityLogService.ACTION_SIGN_REQUEST_INITIATED,
                Map.of(META_USER_DID, userDid,
                        META_REQUEST_ID, requestId,
                        META_INTENT_TYPE, request.intent().type(),
                        "session_id", request.sessionId()));

        // Push notification — stub log nếu chưa wire FCM/APNs (Phase D.4).
        // SEED_EXPORT là special intent, dùng channel push riêng cho UX cảnh báo
        // mạnh hơn (xem spec §9.2).
        if (INTENT_SEED_EXPORT.equals(request.intent().type())) {
            pushService.notifySeedExportRequest(userDid, requestId);
        } else {
            pushService.notifySignRequest(userDid, requestId);
        }

        log.info("SignRequest created: id={}, userDid={}, intent.type={}, sid={}",
                requestId, userDid, request.intent().type(), request.sessionId());

        return new SignRequestCreateResponse(requestId, expiresAt);
    }

    // ──────────────────────────────────────────────────────────────
    // get
    // ──────────────────────────────────────────────────────────────

    @Override
    public SignRequestPayload get(String requestId) {
        return loadPayload(requestId);
    }

    // ──────────────────────────────────────────────────────────────
    // approve
    // ──────────────────────────────────────────────────────────────

    @Override
    public void approve(String requestId, SignApproveRequest request) {
        SignRequestPayload payload = loadPayload(requestId);
        if (!STATUS_PENDING.equals(payload.status())) {
            throw new AppException(ErrorCode.SIGN_REQUEST_EXPIRED,
                    "sign request not pending: " + payload.status());
        }

        // 1. Spec §15.2 — validate intent.timestamp lệch ±60s. Đặt trước nonce
        //    consume để fail-fast: nếu timestamp invalid, không tốn 1 nonce DB row.
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - payload.intent().timestamp()) > TIMESTAMP_SKEW_SEC) {
            log.warn("SignRequest approve: timestamp skew exceeds {}s — now={}, intent.ts={}",
                    TIMESTAMP_SKEW_SEC, now, payload.intent().timestamp());
            throw new AppException(ErrorCode.SIGNATURE_INVALID,
                    "intent timestamp skew exceeds " + TIMESTAMP_SKEW_SEC + "s");
        }

        // 2. Verify pubkey thuộc userDid
        boolean keyOk = authorizedKeyRepository.existsByUserDidAndPublicKeyHexAndStatus(
                payload.userDid(), request.publicKeyHex(), "active");
        if (!keyOk) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID,
                    "publicKeyHex không thuộc userDid của sign request");
        }

        // 3. Consume nonce — chống replay tx cũ. Nonce trong intent.
        nonceService.validateAndConsume(payload.intent().nonce(),
                payload.userDid(), Duration.ofMinutes(5));

        // 4. Verify signature trên canonical intent JSON
        byte[] message = canonicalIntentBytes(payload.intent());
        byte[] sigBytes = hexToBytes(request.signature());
        if (!signatureService.verifyEcdsa(request.publicKeyHex(), message, sigBytes)) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "Sign intent signature invalid");
        }

        // 5. Update Redis: status=approved, kèm signature
        SignRequestPayload approved = new SignRequestPayload(
                payload.requestId(), payload.userDid(), payload.sessionId(),
                payload.intent(), STATUS_APPROVED, payload.expiresAt());
        Duration remaining = Duration.between(Instant.now(), Instant.ofEpochSecond(payload.expiresAt()));
        if (remaining.isNegative() || remaining.isZero()) {
            remaining = Duration.ofSeconds(60); // grace period cho web fetch
        }
        redisService.saveSignRequest(requestId, toJson(approved), remaining);

        // 6. Push SSE event tới web
        Map<String, Object> event = Map.of(
                "status", STATUS_APPROVED,
                "requestId", requestId,
                "signature", request.signature(),
                "publicKeyHex", request.publicKeyHex());
        boolean pushed = sseRegistry.emit(payload.sessionId(), EVENT_SIGNED, event);
        log.info("SignRequest approved: id={}, userDid={}, ssePushed={}",
                requestId, payload.userDid(), pushed);

        UUID approverId = lookupUserId(payload.userDid());
        activityLogService.log(approverId, ActivityLogService.ACTION_SIGN_REQUEST_APPROVED,
                Map.of(META_USER_DID, payload.userDid(),
                        META_REQUEST_ID, requestId,
                        META_INTENT_TYPE, payload.intent().type()));

        // Side-effect SEED_EXPORT: ghi users.seed_exported_at = NOW() (spec §9.5)
        // → dashboard banner cảnh báo bảo mật giảm cho đến khi user xoay khóa.
        if (INTENT_SEED_EXPORT.equals(payload.intent().type())) {
            userRepository.findByUserDid(payload.userDid()).ifPresent(user -> {
                user.setSeedExportedAt(OffsetDateTime.now());
                userRepository.save(user);
                activityLogService.log(user.getId(),
                        ActivityLogService.ACTION_SEED_PHRASE_EXPORTED,
                        Map.of(META_REQUEST_ID, requestId));
            });
        }
    }

    // ──────────────────────────────────────────────────────────────
    // cancel
    // ──────────────────────────────────────────────────────────────

    @Override
    public void cancel(String requestId, String sessionToken) {
        Claims claims = jwtService.parseAndVerify(sessionToken);
        if (!JwtServiceImpl.TYPE_SESSION.equals(claims.get("type", String.class))) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "not a session token");
        }
        String userDid = claims.getSubject();

        SignRequestPayload payload = loadPayload(requestId);
        if (!userDid.equals(payload.userDid())) {
            throw new AppException(ErrorCode.SIGN_REQUEST_NOT_FOUND, "sign request không thuộc user");
        }
        // Idempotency: chỉ cancel khi đang pending. Approved/cancelled rồi → no-op.
        // Tránh race condition mobile-approve và web-cancel ghi đè lên nhau, gây
        // SSE emit "cancelled" sau khi web đã nhận "signed".
        if (!STATUS_PENDING.equals(payload.status())) {
            throw new AppException(ErrorCode.SIGN_REQUEST_EXPIRED,
                    "sign request not pending: " + payload.status());
        }

        SignRequestPayload cancelled = new SignRequestPayload(
                payload.requestId(), payload.userDid(), payload.sessionId(),
                payload.intent(), STATUS_CANCELLED, payload.expiresAt());
        redisService.saveSignRequest(requestId, toJson(cancelled), Duration.ofSeconds(30));

        sseRegistry.emit(payload.sessionId(), EVENT_CANCELLED,
                Map.of("requestId", requestId, "status", STATUS_CANCELLED));
        log.info("SignRequest cancelled: id={}, userDid={}", requestId, userDid);
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private SignRequestPayload loadPayload(String requestId) {
        return redisService.getSignRequest(requestId)
                .map(json -> fromJson(json, SignRequestPayload.class))
                .orElseThrow(() -> new AppException(ErrorCode.SIGN_REQUEST_NOT_FOUND,
                        "sign request: " + requestId));
    }

    /** Soft lookup userId từ userDid — null nếu user record chưa tồn tại
     *  (hiếm: chỉ xảy ra nếu DB inconsistent giữa users + authorized_keys). */
    private UUID lookupUserId(String userDid) {
        return userRepository.findByUserDid(userDid).map(User::getId).orElse(null);
    }

    private byte[] canonicalIntentBytes(SignIntent intent) {
        try {
            return canonicalMapper.writeValueAsBytes(intent);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.SYSTEM_INTERNAL_ERROR,
                    "canonicalize intent failed: " + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return canonicalMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.SYSTEM_INTERNAL_ERROR, "JSON serialize failed");
        }
    }

    private <T> T fromJson(String json, Class<T> cls) {
        try {
            return canonicalMapper.readValue(json, cls);
        } catch (Exception e) {
            throw new AppException(ErrorCode.SYSTEM_INTERNAL_ERROR, "JSON parse failed");
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "signature is null");
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

}
