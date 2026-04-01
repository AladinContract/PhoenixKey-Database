-- ============================================================
-- V1: LÕI ĐỊNH DANH (IDENTITY CORE)
-- users + auth_methods
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- -----------------------------------------------
-- Bảng gốc: users
-- Chỉ lưu DID và timestamp. KHÔNG lưu PII.
-- -----------------------------------------------
CREATE TABLE
    users (
        id UUID PRIMARY KEY, -- UUIDv7 do Backend tạo (không dùng gen_random_uuid())
        user_did VARCHAR(128) UNIQUE NOT NULL, -- did:prism:abc123...
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX idx_users_did ON users (user_did);

-- -----------------------------------------------
-- Bảng ánh xạ Web2 → DID
-- Blind Index: không lưu phone/email plaintext
-- -----------------------------------------------
CREATE TYPE auth_provider AS ENUM ('GOOGLE', 'APPLE', 'PHONE');

CREATE TABLE
    auth_methods (
        id UUID PRIMARY KEY,
        user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        provider auth_provider NOT NULL,
        -- HMAC_SHA256(phone_or_email, SERVER_PEPPER)
        -- Nếu DB bị hack: hacker chỉ thấy hash, không biết số điện thoại
        blind_index_hash VARCHAR(64) UNIQUE NOT NULL,
        -- Phục vụ xoay vòng khóa (Pepper Rotation) trên Vault
        -- Khi rotate pepper: tăng version này lên 2, 3...
        pepper_version INTEGER NOT NULL DEFAULT 1,
        is_verified BOOLEAN NOT NULL DEFAULT FALSE,
        added_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
    );

-- Index chính cho login flow: tìm user qua blind_index_hash
CREATE INDEX idx_blind_index ON auth_methods (blind_index_hash);

CREATE INDEX idx_auth_methods_user ON auth_methods (user_id);