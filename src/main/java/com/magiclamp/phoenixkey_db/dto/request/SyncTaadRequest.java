package com.magiclamp.phoenixkey_db.dto.request;

import com.magiclamp.phoenixkey_db.domain.TaadStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO cho {@code POST /api/v1/internal/sync-taad}.
 *
 * Indexer Worker gọi sau khi quét block mới từ Cardano.
 * PK_DB thực hiện optimistic locking:
 *   - Nếu {@code last_synced_block < new_block} → cập nhật
 *   - Nếu {@code last_synced_block >= new_block} → bỏ qua (stale)
 *   - Nếu {@code block_hash} không khớp → reorg detected → xóa cache
 */
public record SyncTaadRequest(
        @NotBlank(message = "User DID is required")
        String userDid,

        /** Public Key Hash đang kiểm soát DID trên Cardano. */
        @NotBlank(message = "Controller PKH is required")
        String currentControllerPkh,

        /** Sequence số thứ tự giao dịch trên chain. */
        @NotNull(message = "Sequence is required")
        Long sequence,

        /** Trạng thái TAAD: ACTIVE | RECOVERING | MIGRATED */
        @NotNull(message = "Status is required")
        TaadStatus status,

        /** Deadline khôi phục (null nếu ACTIVE). */
        String recoveryDeadline,

        /** Block number đã sync. */
        @NotNull(message = "Last synced block is required")
        Long lastSyncedBlock,

        /** Hash của block đã sync — dùng detect Reorg. */
        @NotBlank(message = "Block hash is required")
        String blockHash
) {}
