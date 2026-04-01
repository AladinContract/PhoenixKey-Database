package com.magiclamp.phoenixkey_db.dto.response;

/**
 * Response DTO cho {@code POST /api/v1/auth/otp/send}.
 *
 * <p>
 * OTP đã được gửi đi (qua SMS/Email do App tự xử lý).
 * PK_DB chỉ confirm: "Đã lưu OTP vào Redis thành công."
 */
public record OtpSendResponse() {

    // Không có result — chỉ là xác nhận thành công.
    // Gọi API → nhận code=1000, message="OTP sent" → App tự hiển thị.
}
