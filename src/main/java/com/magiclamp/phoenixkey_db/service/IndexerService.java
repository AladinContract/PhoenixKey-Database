package com.magiclamp.phoenixkey_db.service;

import com.magiclamp.phoenixkey_db.dto.request.SyncTaadRequest;

/**
 * Service cho Indexer Worker — sync trạng thái TAAD từ Blockchain.
 */
public interface IndexerService {

    /**
     * Sync trạng thái TAAD từ Cardano.
     *
     * <p>
     * Indexer Worker gọi sau khi quét block mới.
     * Optimistic locking:
     * <ul>
     *   <li>{@code last_synced_block < new_block} → cập nhật</li>
     *   <li>{@code last_synced_block >= new_block} → bỏ qua (stale)</li>
     *   <li>{@code block_hash} không khớp → reorg → xóa cache</li>
     * </ul>
     *
     * @param request chứa userDid + pkh + seq + status + block info
     */
    void syncTaad(SyncTaadRequest request);
}
