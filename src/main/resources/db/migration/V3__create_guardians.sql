-- ============================================================
-- V3: MẠNG LƯỚI BẢO HỘ KHÔI PHỤC (SOCIAL GUARDIANS)
-- Vợ/chồng, người thân được user ủy quyền hỗ trợ khôi phục danh tính
-- ============================================================
CREATE TABLE
    guardians (
        id UUID PRIMARY KEY,
        user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        -- DID của người được chỉ định làm Guardian
        guardian_did VARCHAR(128) NOT NULL,
        -- ZERO-TRUST: Chữ ký của User chứng minh họ thực sự mời người này.
        -- Backend phải verify chữ ký trước khi INSERT.
        proof_signature VARCHAR(256) NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'active', -- 'active' | 'revoked'
        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT uq_user_guardian UNIQUE (user_id, guardian_did),
        CONSTRAINT chk_guardian_status CHECK (status IN ('active', 'revoked'))
    );

CREATE INDEX idx_guardians_user ON guardians (user_id)
WHERE
    status = 'active';

CREATE INDEX idx_guardians_guardian_did ON guardians (guardian_did)
WHERE
    status = 'active';