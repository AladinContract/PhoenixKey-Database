package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Bộ đệm trạng thái TAAD đọc từ Blockchain Cardano.
 *
 * Nguyên tắc quan trọng:
 * - Bảng này chỉ được GHI bởi Indexer Worker (đọc từ Cardano)
 * - Không nhận lệnh trực tiếp từ App
 * - Đây là "Kính lúp" để App đọc nhanh, không phải nguồn chân lý
 *
 * Chống Race Condition: Indexer Worker chỉ UPDATE khi
 * {@code last_synced_block < block_number_mới}.
 *
 * Chống Reorg: Nếu {@code block_hash} tại cùng height thay đổi →
 * xóa cache và re-sync từ đầu.
 *
 */
@Entity
@Table(name = "onchain_taad_state_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnchainTaadStateCache {

    /**
     * PK = user_did — 1 DID = 1 dòng cache.
     * FK reference sang users(user_did).
     */
    @Id
    @Column(name = "user_did", length = 128, nullable = false, updatable = false)
    private String userDid;

    /**
     * Public Key Hash của người đang kiểm soát DID này trên Cardano.
     * Thay đổi khi user xoay khóa (key rotation).
     */
    @Column(name = "current_controller_pkh", length = 64, nullable = false)
    private String currentControllerPkh;

    /**
     * Số thứ tự giao dịch trên chain (tăng dần).
     * Dùng để detect stale update — nếu sequence mới <= sequence cũ → bỏ qua.
     */
    @Column(name = "sequence", nullable = false)
    private Long sequence;

    /**
     * Trạng thái TAAD hiện tại.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "taad_status")
    private TaadStatus status;

    /**
     * Deadline khôi phục — lấy từ Time-lock Smart Contract.
     * NULL khi status = ACTIVE.
     *
     */
    @Column(name = "recovery_deadline")
    private OffsetDateTime recoveryDeadline;

    /**
     * Block number cuối cùng mà Indexer Worker đã sync.
     *
     * CHỐNG RACE CONDITION (Optimistic Locking):
     * UPDATE chỉ được thực hiện khi {@code last_synced_block < block_mới}.
     * Nếu block mới <= block đang có → từ chối ghi đè.
     */
    @Column(name = "last_synced_block", nullable = false)
    private Long lastSyncedBlock;

    /**
     * Hash của block đã sync.
     *
     * CHỐNG CHAIN REORG (Rollback):
     * Nếu block_hash tại cùng height thay đổi → reorg detected.
     * → Xóa cache này và re-sync từ đầu.
     */
    @Column(name = "block_hash", length = 64, nullable = false)
    private String blockHash;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ──────────────────────────────────────────────────────────────
    // Business logic helpers
    // ──────────────────────────────────────────────────────────────

    /**
     * Kiểm tra xem block mới có hợp lệ để update không.
     * Dùng trong Indexer Worker.
     *
     * @param newBlock     block number mới từ Cardano
     * @param newBlockHash hash của block mới
     * @return true nếu block mới cao hơn hoặc khác hash (reorg)
     */
    public boolean isBlockNewerOrReorg(long newBlock, String newBlockHash) {
        return newBlock > lastSyncedBlock || (newBlock == lastSyncedBlock && !blockHash.equals(newBlockHash));
    }
}
