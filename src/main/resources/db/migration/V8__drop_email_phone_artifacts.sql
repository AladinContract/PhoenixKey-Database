-- ============================================================
-- V8: Drop email/phone artifacts + thêm seed_exported_at
--
-- Bối cảnh: Sếp đã chốt flow mới — chỉ Hardware Key + biometric trong Secure
-- Enclave mobile, KHÔNG còn email/SĐT/OTP/Blind Index.
--
-- Hệ quả:
--   • auth_methods (V1)         → DROP — không còn ánh xạ Web2 → DID
--   • pending_invitations (V7)  → DROP — Discovery Bridge phụ thuộc blind hash
--   • auth_provider type        → DROP — enum không còn dùng
--   • users.seed_exported_at    → ADD — spec §9.5 dashboard health banner
--
-- ⚠ DESTRUCTIVE: Backup `auth_methods` + `pending_invitations` trước khi chạy
-- trên môi trường production. Migration này KHÔNG migrate dữ liệu cũ — vì flow
-- mới Zero-PII không có chỗ chứa credential.
-- ============================================================

-- 1. Drop bảng + index + FK đi kèm
DROP TABLE IF EXISTS auth_methods CASCADE;
DROP TABLE IF EXISTS pending_invitations CASCADE;

-- 2. Drop enum type
DROP TYPE IF EXISTS auth_provider;

-- 3. Spec §9.5: thêm cột đánh dấu user đã trích xuất Seed Phrase.
--    Khi != NULL → dashboard hiển thị banner cảnh báo (vàng < 72h, đỏ ≥ 72h).
--    Reset về NULL sau khi user thực hiện Key Rotation thành công.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS seed_exported_at TIMESTAMPTZ;

COMMENT ON COLUMN users.seed_exported_at IS
    'Spec §9.5 — thời điểm user trích xuất Seed Phrase qua POST /seed/export-request. NULL = chưa từng export hoặc đã rotate key.';
