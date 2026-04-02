package com.magiclamp.phoenixkey_db.service;

import com.magiclamp.phoenixkey_db.dto.request.SyncTaadRequest;

/**
 * Service cho Indexer Worker — sync trạng thái TAAD từ Blockchain.
 */
public interface IndexerService {

    /**
     * Sync trạng thái TAAD từ Cardano.
     *
     * Indexer Worker gọi sau khi quét block mới.
     * Optimistic locking:
     * - {@code last_synced_block < new_block} → cập nhật
     * - {@code last_synced_block >= new_block} → bỏ qua (stale)
     * - {@code block_hash} không khớp → reorg → xóa cache
     *
     * @param request chứa userDid + pkh + seq + status + block info
     */
    void syncTaad(SyncTaadRequest request);
}
