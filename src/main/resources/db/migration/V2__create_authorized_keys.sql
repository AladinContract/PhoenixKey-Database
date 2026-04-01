-- ============================================================
-- V2: QUẢN LÝ ĐA KHÓA & ĐỊNH TUYẾN LAMPNET
-- Giải quyết bài toán đa thiết bị + Farm Manager
-- ============================================================
CREATE TABLE
    authorized_keys (
        id UUID PRIMARY KEY,
        user_did VARCHAR(128) NOT NULL REFERENCES users (user_did) ON DELETE CASCADE,
        -- Public key của thiết bị phần cứng (iPhone, PC công ty, iPad)
        public_key_hex VARCHAR(128) NOT NULL,
        -- Quyền hạn của thiết bị này
        -- 'owner'        : toàn quyền, thiết bị gốc
        -- 'farm_manager' : ký giao dịch liên quan đến farm
        -- 'read_only'    : chỉ đọc, không ký
        key_role VARCHAR(50) NOT NULL DEFAULT 'owner',
        -- Locator để tìm mảnh khóa trên mạng P2P LampNet
        lampnet_locator_id VARCHAR(128),
        -- ZERO-TRUST: Chữ ký từ Root Key chứng minh việc cấp quyền này là hợp lệ.
        -- Backend PHẢI verify chữ ký này trước khi INSERT.
        -- Nếu Backend bị hack, hacker không thể tự thêm khóa vì không có Root Key.
        added_by_signature VARCHAR(256) NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'active', -- 'active' | 'revoked'
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT uq_did_pubkey UNIQUE (user_did, public_key_hex),
        CONSTRAINT chk_key_role CHECK (
            key_role IN ('owner', 'farm_manager', 'read_only')
        ),
        CONSTRAINT chk_status CHECK (status IN ('active', 'revoked'))
    );

CREATE INDEX idx_authorized_keys_did ON authorized_keys (user_did)
WHERE
    status = 'active';

CREATE INDEX idx_authorized_keys_pubkey ON authorized_keys (public_key_hex);

CREATE INDEX idx_authorized_keys_did_pubkey ON authorized_keys (user_did, public_key_hex);