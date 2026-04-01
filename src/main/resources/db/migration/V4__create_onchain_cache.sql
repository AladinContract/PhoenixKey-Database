-- ============================================================
-- V4: BỘ ĐỆM TRẠNG THÁI ON-CHAIN (INDEXER CACHE)
--
-- NGUYÊN TẮC QUAN TRỌNG:
--   - Bảng này chỉ được GHI bởi Indexer Worker (đọc từ Cardano)
--   - Không nhận lệnh trực tiếp từ App
--   - Đây là "Kính lúp" để App đọc nhanh, không phải nguồn chân lý
-- ============================================================
CREATE TYPE taad_status AS ENUM ('ACTIVE', 'RECOVERING', 'MIGRATED');

CREATE TABLE
    onchain_taad_state_cache (
        -- PK = user_did (1 DID = 1 dòng cache)
        user_did VARCHAR(128) PRIMARY KEY REFERENCES users (user_did),
        -- Public key hiện đang kiểm soát DID này trên Cardano
        current_controller_pkh VARCHAR(64) NOT NULL,
        -- Số thứ tự giao dịch trên chain (tăng dần, dùng để detect stale update)
        sequence BIGINT NOT NULL,
        status taad_status NOT NULL,
        -- Deadline khôi phục (lấy từ Time-lock của Smart Contract)
        -- NULL nếu status = 'ACTIVE'
        recovery_deadline TIMESTAMPTZ,
        -- CHỐNG RACE CONDITION (Optimistic Locking):
        -- Indexer Worker chỉ được UPDATE khi last_synced_block < block_number mới
        last_synced_block BIGINT NOT NULL,
        -- CHỐNG CHAIN REORG:
        -- Nếu block_hash tại cùng height thay đổi → reorg detected
        -- → Xóa cache này và re-sync từ đầu
        block_hash VARCHAR(64) NOT NULL,
        updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
    );

-- Index cho Indexer Worker khi quét batch
CREATE INDEX idx_taad_cache_status ON onchain_taad_state_cache (status);

CREATE INDEX idx_taad_cache_last_block ON onchain_taad_state_cache (last_synced_block);