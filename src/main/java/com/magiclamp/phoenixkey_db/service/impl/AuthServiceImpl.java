package com.magiclamp.phoenixkey_db.service.impl;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.magiclamp.phoenixkey_db.domain.AuthMethod;
import com.magiclamp.phoenixkey_db.crypto.BlindIndexService;
import com.magiclamp.phoenixkey_db.dto.request.OtpSendRequest;
import com.magiclamp.phoenixkey_db.dto.request.OtpVerifyRequest;
import com.magiclamp.phoenixkey_db.dto.response.OtpVerifyResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.AuthMethodRepository;
import com.magiclamp.phoenixkey_db.service.ActivityLogService;
import com.magiclamp.phoenixkey_db.service.AuthService;
import com.magiclamp.phoenixkey_db.service.RedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final RedisService redisService;
    private final ActivityLogService activityLogService;
    private final AuthMethodRepository authMethodRepository;
    private final BlindIndexService blindIndexService;

    private static final String REASON = "reason";

    // ──────────────────────────────────────────────────────────────
    // Save OTP — NestJS gọi sau khi generate OTP
    // ──────────────────────────────────────────────────────────────

    /**
     * Lưu OTP vào Redis.
     *
     * Flow:
     * - NestJS nhận credential từ App
     * - NestJS hash(credential) → blind_hash
     * - NestJS generate OTP
     * - NestJS gửi OTP qua SMS/Email
     * - NestJS gọi endpoint này để lưu OTP + credential vào Redis
     *
     * PK_DB nhận blind_hash + otp + credential.
     * Credential được dùng để re-hash blind_index_hash khi pepper rotate —
     * không lưu vào DB (Zero-PII).
     *
     * @param request chứa blind_hash + otp + provider + credential
     */
    @Override
    public void saveOtp(OtpSendRequest request) {
        // Lưu OTP + credential vào Redis
        redisService.saveOtp(request.blindHash(), request.otp(), request.credential());

        log.info("OTP saved: blind_hash={}, provider={}", request.blindHash(), request.provider());

        activityLogService.log(
                ActivityLogService.ACTION_OTP_SENT,
                Map.of("provider", request.provider().name()));
    }

    // ──────────────────────────────────────────────────────────────
    // Verify OTP — App gọi sau khi nhận OTP qua SMS/Email
    // ──────────────────────────────────────────────────────────────

    /**
     * Verify OTP trong Redis.
     *
     * Flow:
     * - App nhận OTP qua SMS/Email (từ NestJS)
     * - NestJS trả blind_hash cho App
     * - App gọi endpoint này với blind_hash + otp
     * - PK_DB lookup Redis bằng blind_hash → so sánh OTP
     * - Đúng → set is_verified = true
     *
     * @param request chứa blind_hash + otp
     * @return user_did nếu user đã đăng ký, null nếu user mới
     */
    @Override
    @Transactional
    public OtpVerifyResponse verifyOtp(OtpVerifyRequest request) {
        String blindHash = request.blindHash();
        String otp = request.otp();

        // Tra OTP trong Redis
        Optional<String> storedOtp = redisService.getOtp(blindHash);
        if (storedOtp.isEmpty()) {
            activityLogService.log(
                    ActivityLogService.ACTION_LOGIN_FAILED,
                    Map.of(REASON, "otp_not_found"));
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        // Kiểm tra số lần sai
        long attempts = redisService.getOtpAttempts(blindHash);
        if (attempts >= 5) {
            redisService.deleteOtp(blindHash);
            activityLogService.log(
                    ActivityLogService.ACTION_OTP_FAILED,
                    Map.of(REASON, "max_attempts_exceeded"));
            throw new AppException(ErrorCode.OTP_EXCEEDED_ATTEMPTS);
        }

        // So sánh OTP
        if (!storedOtp.get().equals(otp)) {
            long newAttempts = redisService.incrementOtpAttempts(blindHash);
            log.warn("OTP mismatch: blind_hash={}, attempt={}", blindHash, newAttempts);
            activityLogService.log(
                    ActivityLogService.ACTION_LOGIN_FAILED,
                    Map.of(REASON, "invalid_otp"));
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        // OTP đúng → lấy credential từ Redis (dùng để re-hash sau)
        Optional<String> credentialOpt = redisService.getCredential(blindHash);

        // Xóa OTP + credential khỏi Redis
        redisService.deleteOtp(blindHash);
        redisService.resetOtpAttempts(blindHash);

        // Tìm user qua blind hash
        AuthMethod authMethod = authMethodRepository.findByBlindIndexHash(blindHash)
                .orElse(null);

        String userDid = null;
        if (authMethod != null) {
            // User đã đăng ký
            int currentVersion = blindIndexService.getCurrentPepperVersion();
            boolean needsRehash = authMethod.getPepperVersion() < currentVersion;

            if (needsRehash && credentialOpt.isPresent()) {
                // Pepper đã rotate → re-hash blind_index_hash với pepper mới
                String newBlindHash = blindIndexService.hash(credentialOpt.get());
                authMethod.setBlindIndexHash(newBlindHash);
                authMethod.setPepperVersion(currentVersion);
                log.info("Blind index re-hashed: userDid={}, old_version={}, new_version={}",
                        authMethod.getUser().getUserDid(),
                        authMethod.getPepperVersion(), currentVersion);
            }

            authMethod.setIsVerified(true);
            authMethodRepository.save(authMethod);
            userDid = authMethod.getUser().getUserDid();

            activityLogService.log(
                    authMethod.getUser().getId(),
                    ActivityLogService.ACTION_LOGIN_SUCCESS,
                    Map.of("provider", authMethod.getProvider().name(),
                            "pepper_rehashed", String.valueOf(needsRehash)));
        } else {
            // User mới → OTP đúng nhưng chưa đăng ký
            activityLogService.log(
                    ActivityLogService.ACTION_LOGIN_SUCCESS,
                    Map.of("new_user", "true"));
        }

        return new OtpVerifyResponse(userDid, blindHash);
    }
}
