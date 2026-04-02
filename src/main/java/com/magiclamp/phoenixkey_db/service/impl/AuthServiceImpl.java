package com.magiclamp.phoenixkey_db.service.impl;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.magiclamp.phoenixkey_db.domain.AuthMethod;
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

    private static final String REASON = "reason";

    // ──────────────────────────────────────────────────────────────
    // Save OTP — NestJS gọi sau khi generate OTP
    // ──────────────────────────────────────────────────────────────

    /**
     * Lưu OTP vào Redis.
     *
     * <p>
     * Flow:
     * <ol>
     * <li>NestJS nhận credential từ App</li>
     * <li>NestJS hash(credential) → blind_hash</li>
     * <li>NestJS generate OTP</li>
     * <li>NestJS gửi OTP qua SMS/Email</li>
     * <li>NestJS gọi endpoint này để lưu OTP vào Redis</li>
     * </ol>
     *
     * <p>
     * PK_DB chỉ nhận blind_hash + otp. Không biết email/phone thật.
     *
     * @param request chứa blind_hash + otp + provider
     */
    @Override
    public void saveOtp(OtpSendRequest request) {
        String blindHash = request.blindHash();
        String otp = request.otp();

        // Lưu OTP vào Redis
        redisService.saveOtp(blindHash, otp);

        log.info("OTP saved: blind_hash={}, provider={}", blindHash, request.provider());

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
     * <p>
     * Flow:
     * <ol>
     * <li>App nhận OTP qua SMS/Email (từ NestJS)</li>
     * <li>NestJS trả blind_hash cho App</li>
     * <li>App gọi endpoint này với blind_hash + otp</li>
     * <li>PK_DB lookup Redis bằng blind_hash → so sánh OTP</li>
     * <li>Đúng → set is_verified = true</li>
     * </ol>
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

        // OTP đúng → xóa khỏi Redis
        redisService.deleteOtp(blindHash);
        redisService.resetOtpAttempts(blindHash);

        // Tìm user qua blind hash
        AuthMethod authMethod = authMethodRepository.findByBlindIndexHash(blindHash)
                .orElse(null);

        String userDid = null;
        if (authMethod != null) {
            // User đã đăng ký → cập nhật is_verified
            authMethod.setIsVerified(true);
            authMethodRepository.save(authMethod);
            userDid = authMethod.getUser().getUserDid();
            activityLogService.log(
                    authMethod.getUser().getId(),
                    ActivityLogService.ACTION_LOGIN_SUCCESS,
                    Map.of("provider", authMethod.getProvider().name()));
        } else {
            // User mới → OTP đúng nhưng chưa đăng ký
            activityLogService.log(
                    ActivityLogService.ACTION_LOGIN_SUCCESS,
                    Map.of("new_user", "true"));
        }

        return new OtpVerifyResponse(userDid, blindHash);
    }
}
