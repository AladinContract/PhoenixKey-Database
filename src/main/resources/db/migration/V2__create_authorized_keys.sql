-- ============================================================
-- V2: QUẢN LÝ ĐA KHÓA & ĐỊNH TUYẾN LAMPNET
-- Giải quyết bài toán đa thiết bị + Farm Manager
-- [V1.5] Thêm key_origin_type ENUM + cột key_origin
-- [V1.5] Xóa lampnet_locator_id (tính on-the-fly)
-- ============================================================

-- -----------------------------------------------
-- [V1.5] Key Origin Type — biết nguồn gốc Hardware Key
-- SDK cần biết để quyết định có tìm mảnh trên LampNet
-- khi Recovery hay không.
-- -----------------------------------------------
CREATE TYPE key_origin_type AS ENUM (
    'SECURE_ENCLAVE', -- Key sinh trong Secure Enclave/TEE — có mảnh trên LampNet
    'IMPORTED_BIP39', -- Seed phrase nhập từ ngoài (Yoroi, Eternl...) — KHÔNG có mảnh LampNet
    'DERIVED_CHILD'   -- Key derive từ seed gốc — dự phòng cho tương lai
);

-- -----------------------------------------------
-- Bảng authorized_keys
-- [V1.5] Xóa lampnet_locator_id
-- LampNet là mạng phân tán — topology thay đổi liên tục.
-- Locator = Hash(public_key_hex + SALT) tính on-the-fly.
-- Lưu vào DB → stale data.
-- -----------------------------------------------
CREATE TABLE
    authorized_keys (
        id UUID PRIMARY KEY,
        user_did VARCHAR(128) NOT NULL REFERENCES users (user_did) ON DELETE CASCADE,
        -- Public key của thiết bị phần cứng (iPhone, PC công ty, iPad)
        -- Không bao giờ rời khỏi chip Secure Enclave/TEE
        public_key_hex VARCHAR(256) NOT NULL,
        -- [V1.5] Nguồn gốc key — quyết định LampNet có được dùng hay không
        key_origin key_origin_type NOT NULL DEFAULT 'SECURE_ENCLAVE',
        -- Quyền hạn của thiết bị này
        -- 'owner'        : toàn quyền, thiết bị gốc
        -- 'manager'      : ký giao dịch (farm_manager → manager)
        -- 'viewer'       : chỉ đọc, không ký (read_only → viewer)
        key_role VARCHAR(50) NOT NULL DEFAULT 'owner',
        -- ZERO-TRUST: Chữ ký từ Root Key chứng minh việc cấp quyền này là hợp lệ.
        -- Backend PHẢI verify chữ ký này trước khi INSERT.
        -- Nếu Backend bị hack, hacker không thể tự thêm khóa vì không có Root Key.
        added_by_signature VARCHAR(256) NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'active', -- 'active' | 'revoked'
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        -- [V1.5] Xóa lampnet_locator_id — tính on-the-fly
        CONSTRAINT uq_did_pubkey UNIQUE (user_did, public_key_hex),
        CONSTRAINT chk_key_role CHECK (
            key_role IN ('owner', 'manager', 'viewer')
        ),
        CONSTRAINT chk_status CHECK (status IN ('active', 'revoked'))
    );

CREATE INDEX idx_authorized_keys_did ON authorized_keys (user_did)
WHERE
    status = 'active';

CREATE INDEX idx_authorized_keys_pubkey ON authorized_keys (public_key_hex);

CREATE INDEX idx_authorized_keys_did_pubkey ON authorized_keys (user_did, public_key_hex);