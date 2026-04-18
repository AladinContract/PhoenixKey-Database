package com.magiclamp.phoenixkey_db.service;

import java.time.Duration;

/**
 * [V1.5] Service chống Replay Attack cho signing flow.
 *
 * signData() flow bắt buộc:
 *   1. Nhận request ký có kèm nonce
 *   2. Gọi validateAndConsume(nonce, userDid, ttl) → REJECT nếu đã tồn tại
 *   3. Verify chữ ký với public_key_hex
 *   4. Thực thi logic nghiệp vụ
 *   5. (Service ghi nonce → done trong bước 2)
 *
 * @see com.magiclamp.phoenixkey_db.service.impl.NonceServiceImpl
 */
public interface NonceService {

    /**
     * Kiểm tra nonce chưa tồn tại → INSERT → return success.
     *
     * @param nonce   nonce từ signing request
     * @param userDid DID của user ký
     * @param ttl     thời gian sống của nonce (thường 5 phút)
     * @throws com.magiclamp.phoenixkey_db.exception.AppException(ErrorCode.NONCE_ALREADY_USED) nếu nonce đã tồn tại
     */
    void validateAndConsume(String nonce, String userDid, Duration ttl);

    /**
     * Xóa tất cả nonce đã hết hạn.
     * Dùng cho cronjob chạy mỗi giờ.
     *
     * @return số dòng đã xóa
     */
    int deleteExpired();
}
