package com.magiclamp.phoenixkey_db.service;

import com.magiclamp.phoenixkey_db.dto.request.OtpSendRequest;
import com.magiclamp.phoenixkey_db.dto.request.OtpVerifyRequest;
import com.magiclamp.phoenixkey_db.dto.response.OtpVerifyResponse;

/**
 * Service xử lý OTP — NestJS gửi và verify OTP.
 *
 * <p>
 * <b>Lưu ý quan trọng:</b>
 * PK_DB KHÔNG generate OTP, KHÔNG gửi SMS/Email.
 * NestJS làm việc này qua Twilio/SendGrid.
 * PK_DB chỉ:
 * <ul>
 * <li>Nhận blind_hash + otp từ NestJS → lưu vào Redis</li>
 * <li>Nhận blind_hash + otp từ App → verify trong Redis</li>
 * </ul>
 */
public interface AuthService {

    /**
     * NestJS gọi để lưu OTP vào Redis.
     *
     * <p>
     * NestJS nhận credential từ App, hash → blind_hash,
     * generate OTP, gửi SMS/Email, rồi gọi endpoint này để lưu vào Redis.
     *
     * @param request chứa blind_hash + otp + provider
     */
    void saveOtp(OtpSendRequest request);

    /**
     * App gọi để verify OTP sau khi nhận mã qua SMS/Email.
     *
     * <p>
     * App gửi blind_hash (NestJS trả về) + otp.
     * PK_DB lookup Redis → so sánh OTP.
     * Đúng → set is_verified = true.
     *
     * @param request chứa blind_hash + otp
     * @return userDid (nếu đã đăng ký) + blindHash
     */
    OtpVerifyResponse verifyOtp(OtpVerifyRequest request);
}
