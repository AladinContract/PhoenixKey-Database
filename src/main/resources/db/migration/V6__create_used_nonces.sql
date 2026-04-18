-- ============================================================
-- V6: CHỐNG TẤN CÔNG REPLAY (REPLAY ATTACK PROTECTION)
-- [V1.5] Bảng used_nonces
--
-- Quy tắc signData() bắt buộc:
--   1. Nhận request ký có kèm nonce
--   2. Kiểm tra nonce trong used_nonces → Nếu tồn tại: REJECT
--   3. Verify chữ ký với public_key_hex
--   4. Thực thi logic nghiệp vụ
--   5. Ghi nonce vào used_nonces với expires_at
--
-- Tại sao PostgreSQL thay vì Redis cho nonce?
-- Redis TTL không đủ bảo đảm — nonce có thể bị tái sử dụng
-- nếu TTL hết đúng lúc request thứ hai đến.
-- PostgreSQL với PRIMARY KEY đảm bảo tính duy nhất tuyệt đối.
-- ============================================================
CREATE TABLE used_nonces (
    nonce VARCHAR(64) NOT NULL,
    user_did VARCHAR(128) NOT NULL REFERENCES users(user_did) ON DELETE CASCADE,
    used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (nonce, user_did)
);

-- Index cho cleanup query (xóa nonce hết hạn mỗi giờ)
CREATE INDEX idx_nonce_expiry ON used_nonces(expires_at);

-- ============================================================
-- Cleanup cronjob (chạy mỗi giờ bằng pg_cron hoặc OS cron):
-- DELETE FROM used_nonces WHERE expires_at < NOW();
-- ============================================================
