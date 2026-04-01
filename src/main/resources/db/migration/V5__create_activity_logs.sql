-- ============================================================
-- V5: NHẬT KÝ KIỂM TOÁN BẤT BIẾN (IMMUTABLE AUDIT TRAIL)
--
-- NGUYÊN TẮC: Append-Only. Không ai được UPDATE hoặc DELETE.
-- Trigger dưới đây enforce điều này ở tầng Database.
-- ============================================================
CREATE TABLE
    activity_logs (
        -- UUIDv7: tự động sort theo thời gian, hiệu năng insert tốt hơn UUIDv4
        id UUID PRIMARY KEY,
        user_id UUID REFERENCES users (id), -- nullable: log trước khi user tồn tại
        -- VD: 'login_success', 'login_failed', 'otp_sent',
        --     'key_authorized', 'key_revoked',
        --     'recovery_initiated', 'recovery_approved',
        --     'taad_synced', 'guardian_added'
        action VARCHAR(50) NOT NULL,
        -- Metadata linh hoạt: IP hash, OS version, device fingerprint...
        -- TUYỆT ĐỐI KHÔNG CHỨA PII (phone, email, tên, địa chỉ)
        metadata JSONB,
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
    );

-- Sort theo thời gian (UUIDv7 đã có timestamp prefix, index này tăng tốc range query)
CREATE INDEX idx_activity_logs_user ON activity_logs (user_id, created_at DESC);

CREATE INDEX idx_activity_logs_action ON activity_logs (action, created_at DESC);

CREATE INDEX idx_activity_logs_created ON activity_logs (created_at DESC);

-- -----------------------------------------------
-- TRIGGER BẢO VỆ TÍNH BẤT BIẾN
-- Chặn mọi hành vi UPDATE hoặc DELETE trên bảng này.
-- Kể cả DBA cũng không được sửa log — phải audit qua cơ chế khác.
-- -----------------------------------------------
CREATE OR REPLACE FUNCTION prevent_log_tampering()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'IMMUTABILITY_VIOLATION: activity_logs là Append-Only. '
        'Hành vi % bị nghiêm cấm. Mọi audit trail phải được bảo toàn.',
        TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER enforce_append_only BEFORE
UPDATE
OR DELETE ON activity_logs FOR EACH ROW EXECUTE FUNCTION prevent_log_tampering ();