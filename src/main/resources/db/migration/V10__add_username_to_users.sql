-- ============================================================
-- V10: USERNAME — Tên hiển thị dễ nhớ cho đăng nhập
--
-- Thiết kế:
--   - Không thay thế DID (DID vẫn là anchor định danh thật)
--   - Username = lookup shortcut → tìm DID → mobile approve QR
--   - Lưu lowercase, so sánh lowercase (case-insensitive by convention)
--   - 30-day cooldown đổi username (enforce tại application layer)
--   - Reserved names enforce tại application layer (không phải DB)
-- ============================================================

ALTER TABLE users
    ADD COLUMN username         VARCHAR(32)  UNIQUE,
    ADD COLUMN username_set_at  TIMESTAMPTZ;

-- Case-insensitive lookup: lower(username)
CREATE UNIQUE INDEX idx_users_username_lower
    ON users (lower(username))
    WHERE username IS NOT NULL;

-- Fast lookup khi resolve username → DID
CREATE INDEX idx_users_username
    ON users (username)
    WHERE username IS NOT NULL;

COMMENT ON COLUMN users.username        IS 'Tên hiển thị: 3-32 ký tự [a-z0-9_]. Nullable — user có thể chưa đặt. Bất biến 30 ngày sau khi đặt.';
COMMENT ON COLUMN users.username_set_at IS 'Thời điểm đặt username lần cuối — dùng để enforce 30-day cooldown.';
