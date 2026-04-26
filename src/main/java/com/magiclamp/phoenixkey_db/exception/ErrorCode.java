package com.magiclamp.phoenixkey_db.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi cho toàn bộ PhoenixKey Server.
 *
 * Quy tắc đánh số:
 * - 13xx — Web Session (QR pairing + SSE)
 * - 14xx — Sign Request (web ↔ mobile relay)
 * - 2xxx — User / DID
 * - 3xxx — Authorized Key / Thiết bị
 * - 4xxx — Guardian / Social Recovery
 * - 5xxx — TAAD / On-chain State / Cardano
 * - 9xxx — System errors
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ──────────────────────────────────────────────────────────────
    // 13xx — Web Session (QR pairing + SSE)
    // ──────────────────────────────────────────────────────────────

    /** Session id không tìm thấy hoặc temp_token sai. */
    SESSION_NOT_FOUND(1301, "Session not found", HttpStatus.NOT_FOUND),

    /** Session đã quá TTL (5 phút). */
    SESSION_EXPIRED(1302, "Session expired", HttpStatus.GONE),

    /** Session đã được approve trước đó — không thể approve lại. */
    SESSION_ALREADY_APPROVED(1303, "Session already approved", HttpStatus.CONFLICT),

    // ──────────────────────────────────────────────────────────────
    // 14xx — Sign Request (web ↔ mobile relay)
    // ──────────────────────────────────────────────────────────────

    /** Sign request id không tìm thấy. */
    SIGN_REQUEST_NOT_FOUND(1401, "Sign request not found", HttpStatus.NOT_FOUND),

    /** Sign request đã quá TTL (120s). */
    SIGN_REQUEST_EXPIRED(1402, "Sign request expired", HttpStatus.GONE),

    /** Chữ ký Hardware Key không hợp lệ. */
    SIGNATURE_INVALID(1403, "Invalid signature", HttpStatus.FORBIDDEN),

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

    /** [V1.5] Nonce đã được sử dụng — replay attack detected. */
    NONCE_ALREADY_USED(3006, "Nonce already used", HttpStatus.CONFLICT),

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

    /** Tx submit Cardano fail (Blockfrost reject hoặc timeout confirm). */
    CARDANO_TX_FAILED(5101, "Cardano transaction failed", HttpStatus.BAD_GATEWAY),

    /** Resolve DID Document fail — tx hash không tìm thấy hoặc datum invalid. */
    CARDANO_RESOLVE_FAILED(5102, "Failed to resolve DID Document on Cardano", HttpStatus.BAD_GATEWAY),

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
