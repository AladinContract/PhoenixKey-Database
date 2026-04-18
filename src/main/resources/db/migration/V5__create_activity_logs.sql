-- ============================================================
-- V5: NHẬT KÝ KIỂM TOÁN BẤT BIẾN (IMMUTABLE AUDIT TRAIL)
-- [V1.5] Partitioned table + smart trigger + FK ON DELETE SET NULL
--
-- Strategy: handle cả fresh install lẫn upgrade từ v1.0
-- ============================================================

-- Bước 1: Nếu bảng cũ tồn tại và KHÔNG phải partitioned → backup + drop
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'activity_logs' AND n.nspname = 'public' AND c.relkind = 'r'
    ) THEN
        CREATE TABLE activity_logs_backup AS SELECT * FROM activity_logs;
        DROP TABLE activity_logs;
    END IF;
END $$;

-- Bước 2: Tạo partitioned table (idempotent)
CREATE TABLE IF NOT EXISTS activity_logs (
    id         UUID NOT NULL,
    user_id    UUID,                  -- nullable: GDPR ON DELETE SET NULL
    action     VARCHAR(50) NOT NULL,
    metadata   JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Bước 3: Tạo partitions Q2 + Q3 2026
CREATE TABLE IF NOT EXISTS activity_logs_2026_q2
    PARTITION OF activity_logs
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');

CREATE TABLE IF NOT EXISTS activity_logs_2026_q3
    PARTITION OF activity_logs
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');

-- Bước 4: Migrate dữ liệu từ backup (chỉ khi có dữ liệu cũ)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'activity_logs_backup' AND n.nspname = 'public'
    ) THEN
        INSERT INTO activity_logs (id, user_id, action, metadata, created_at)
        SELECT id, user_id, action, metadata, created_at
        FROM activity_logs_backup;

        DROP TABLE activity_logs_backup;
    END IF;
END $$;

-- Bước 5: FK user_id → ON DELETE SET NULL (GDPR)
ALTER TABLE activity_logs
    DROP CONSTRAINT IF EXISTS activity_logs_user_id_fkey,
    ADD CONSTRAINT activity_logs_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

-- Bước 6: Trigger thông minh
--   [1] Chặn UPDATE: không ai được sửa nội dung log
--   [2] Chặn DELETE khi user_id IS NOT NULL: bảo vệ audit trail đang active
--   [3] Cho phép DELETE khi user_id IS NULL: GDPR erasure hợp lệ
CREATE OR REPLACE FUNCTION smart_audit_trigger()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'IMMUTABILITY_VIOLATION: Logs are read-only.';
    END IF;
    IF TG_OP = 'DELETE' AND OLD.user_id IS NOT NULL THEN
        RAISE EXCEPTION 'IMMUTABILITY_VIOLATION: Cannot delete active audit trails.';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS smart_audit_trigger ON activity_logs;
CREATE TRIGGER smart_audit_trigger
BEFORE UPDATE OR DELETE ON activity_logs
FOR EACH ROW EXECUTE FUNCTION smart_audit_trigger();

-- Bước 7: Indexes
CREATE INDEX IF NOT EXISTS idx_activity_logs_action
    ON activity_logs (action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_logs_created
    ON activity_logs (created_at DESC);

-- ============================================================
-- Partition management (chạy đầu mỗi quý):
-- CREATE TABLE activity_logs_2026_q4
--     PARTITION OF activity_logs
--     FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');
-- ============================================================
