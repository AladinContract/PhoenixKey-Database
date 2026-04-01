package com.magiclamp.phoenixkey_db.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi cho toàn bộ PhoenixKey Database.
 *
 * <p>
 * Quy tắc đánh số:
 * <ul>
 * <li>1xxx — Auth / Đăng nhập / OTP</li>
 * <li>2xxx — User / DID</li>
 * <li>3xxx — Authorized Key / Thiết bị</li>
 * <li>4xxx — Guardian / Social Recovery</li>
 * <li>5xxx — TAAD / On-chain State</li>
 * <li>9xxx — System errors</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ──────────────────────────────────────────────────────────────
    // 1xxx — Auth / Đăng nhập / OTP
    // ──────────────────────────────────────────────────────────────

    /** Blind hash không tìm thấy — chưa đăng ký. */
    AUTH_METHOD_NOT_FOUND(1001, "Auth method not found", HttpStatus.NOT_FOUND),

    /** Blind hash đã tồn tại — user đã đăng ký với credential này. */
    AUTH_METHOD_ALREADY_EXISTS(1002, "Auth method already registered", HttpStatus.CONFLICT),

    /** OTP sai hoặc đã hết hạn. */
    OTP_INVALID(1101, "Invalid or expired OTP", HttpStatus.BAD_REQUEST),

    /** OTP đã nhập sai quá số lần cho phép. */
    OTP_EXCEEDED_ATTEMPTS(1102, "OTP attempts exceeded", HttpStatus.TOO_MANY_REQUESTS),

    /** OTP đã hết hạn (TTL). */
    OTP_EXPIRED(1103, "OTP has expired", HttpStatus.BAD_REQUEST),

    /** Provider (google/apple/phone) không hợp lệ. */
    AUTH_PROVIDER_INVALID(1201, "Invalid auth provider", HttpStatus.BAD_REQUEST),

    /** Auth method chưa được xác minh — chưa nhập OTP. */
    AUTH_METHOD_NOT_VERIFIED(1202, "Auth method not verified", HttpStatus.FORBIDDEN),

    // ──────────────────────────────────────────────────────────────
    // 2xxx — User / DID
    // ──────────────────────────────────────────────────────────────

    /** User không tìm thấy qua ID. */
    USER_NOT_FOUND(2001, "User not found", HttpStatus.NOT_FOUND),

    /** User không tìm thấy qua DID. */
    USER_DID_NOT_FOUND(2002, "User with this DID not found", HttpStatus.NOT_FOUND),

    /** DID đã tồn tại — đã đăng ký rồi. */
    USER_DID_ALREADY_EXISTS(2003, "DID already registered", HttpStatus.CONFLICT),

    // ──────────────────────────────────────────────────────────────
    // 3xxx — Authorized Key / Thiết bị
    // ──────────────────────────────────────────────────────────────

    /** Public key đã được authorized cho user này. */
    KEY_ALREADY_AUTHORIZED(3001, "Key already authorized", HttpStatus.CONFLICT),

    /** Khóa không tìm thấy. */
    KEY_NOT_FOUND(3002, "Authorized key not found", HttpStatus.NOT_FOUND),

    /** Chữ ký Zero-Trust không hợp lệ — không phải từ Root Key. */
    KEY_SIGNATURE_INVALID(3003, "Invalid key authorization signature", HttpStatus.FORBIDDEN),

    /** Khóa đã bị revoke trước đó. */
    KEY_ALREADY_REVOKED(3004, "Key already revoked", HttpStatus.CONFLICT),

    /** Khóa đang ở trạng thái không hợp lệ. */
    KEY_STATUS_INVALID(3005, "Invalid key status", HttpStatus.BAD_REQUEST),

    // ──────────────────────────────────────────────────────────────
    // 4xxx — Guardian / Social Recovery
    // ──────────────────────────────────────────────────────────────

    /** Guardian không tìm thấy. */
    GUARDIAN_NOT_FOUND(4001, "Guardian not found", HttpStatus.NOT_FOUND),

    /** Guardian đã tồn tại cho user này. */
    GUARDIAN_ALREADY_EXISTS(4002, "Guardian already exists for this user", HttpStatus.CONFLICT),

    /** Chữ ký Zero-Trust guardian không hợp lệ. */
    GUARDIAN_SIGNATURE_INVALID(4003, "Invalid guardian proof signature", HttpStatus.FORBIDDEN),

    /** Guardian chưa đạt ngưỡng tối thiểu (cần >= 3). */
    GUARDIAN_INSUFFICIENT(4004, "Minimum guardians not met (need at least 3)", HttpStatus.BAD_REQUEST),

    /** Guardian đã bị revoke. */
    GUARDIAN_ALREADY_REVOKED(4005, "Guardian already revoked", HttpStatus.CONFLICT),

    // ──────────────────────────────────────────────────────────────
    // 5xxx — TAAD / On-chain State
    // ──────────────────────────────────────────────────────────────

    /** TAAD state cache không tìm thấy. */
    TAAD_STATE_NOT_FOUND(5001, "TAAD state not found", HttpStatus.NOT_FOUND),

    /** TAAD state cache bị stale — block thấp hơn đang có (race condition). */
    TAAD_STATE_STALE(5002, "Stale TAAD state — block height too low", HttpStatus.CONFLICT),

    /** TAAD state bị Reorg detected — hash không khớp. */
    TAAD_REORG_DETECTED(5003, "Blockchain reorganization detected", HttpStatus.CONFLICT),

    /** User đang trong trạng thái RECOVERING — không cho thao tác. */
    TAAD_IN_RECOVERY_MODE(5004, "Account is in recovery mode", HttpStatus.FORBIDDEN),

    /** Sequence không đúng — có thể bị replay attack. */
    TAAD_SEQUENCE_MISMATCH(5005, "Sequence mismatch — possible replay attack", HttpStatus.BAD_REQUEST),

    // ──────────────────────────────────────────────────────────────
    // 9xxx — System errors
    // ──────────────────────────────────────────────────────────────

    /** Giá trị enum không hợp lệ. */
    ENUM_INVALID_VALUE(9800, "Invalid enum value", HttpStatus.BAD_REQUEST),

    /** Lỗi không xác định. */
    SYSTEM_UNKNOWN_ERROR(9998, "System unknown error", HttpStatus.INTERNAL_SERVER_ERROR),

    /** Lỗi nội bộ server. */
    SYSTEM_INTERNAL_ERROR(9999, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus status;
}
