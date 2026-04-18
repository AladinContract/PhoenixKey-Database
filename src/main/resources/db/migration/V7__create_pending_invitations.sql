-- ============================================================
-- V7: DISCOVERY BRIDGE — PENDING INVITATIONS
-- [V1.5] Bảng pending_invitations
--
-- Luồng Discovery Bridge:
--   1. User A nhập SĐT/Email của User B để mời làm Guardian
--   2. Backend tính blind_index_hash của User B
--   3. Nếu User B chưa có app → ghi vào pending_invitations
--   4. Khi User B đăng ký bằng SĐT/Email đó:
--      → Backend match blind_index_hash
--      → Tự động resolve: ghi guardian_did vào guardians
--      → Xóa dòng pending_invitations
-- ============================================================
CREATE TABLE pending_invitations (
    id UUID PRIMARY KEY,
    inviter_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_blind_hash VARCHAR(64) NOT NULL, -- HMAC_SHA256(phone_or_email, SERVER_PEPPER)
    invite_type VARCHAR(20) NOT NULL, -- 'guardian' | 'manager'
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending', -- 'pending' | 'resolved' | 'expired'
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_invite_type CHECK (invite_type IN ('guardian', 'manager')),
    CONSTRAINT chk_invitation_status CHECK (status IN ('pending', 'resolved', 'expired'))
);

-- Index: tìm lời mời theo invitee blind hash (dùng khi user đăng ký)
CREATE INDEX idx_invitee_hash ON pending_invitations(invitee_blind_hash);

-- Index: tìm lời mời theo người mời
CREATE INDEX idx_inviter_user ON pending_invitations(inviter_user_id);

-- ============================================================
-- Cleanup cronjob (chạy mỗi giờ):
-- UPDATE pending_invitations
-- SET status = 'expired'
-- WHERE status = 'pending' AND expires_at < NOW();
-- ============================================================
