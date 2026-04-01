package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Bộ đệm trạng thái TAAD đọc từ Blockchain Cardano.
 *
 * <p>
 * <b>Nguyên tắc quan trọng:</b>
 * <ul>
 *   <li>Bảng này chỉ được GHI bởi Indexer Worker (đọc từ Cardano)</li>
 *   <li>Không nhận lệnh trực tiếp từ App</li>
 *   <li>Đây là "Kính lúp" để App đọc nhanh, không phải nguồn chân lý</li>
 * </ul>
 *
 * <p>
 * Chống Race Condition: Indexer Worker chỉ UPDATE khi
 * {@code last_synced_block < block_number_mới}.
 *
 * <p>
 * Chống Reorg: Nếu {@code block_hash} tại cùng height thay đổi →
 * xóa cache và re-sync từ đầu.
 *
 * @see TaadStatus
 * @see <a href="https://github.com/AladinContract/PhoenixKey/tree/main/PoCs/Governance">TAAD
 *      Smart Contract</a>
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
     * @see <a href="https://github.com/AladinContract/PhoenixKey/tree/main/PoCs/Governance">Time-lock
     *      Logic</a>
     */
    @Column(name = "recovery_deadline")
    private OffsetDateTime recoveryDeadline;

    /**
     * Block number cuối cùng mà Indexer Worker đã sync.
     *
     * <p>
     * CHỐNG RACE CONDITION (Optimistic Locking):
     * UPDATE chỉ được thực hiện khi {@code last_synced_block < block_mới}.
     * Nếu block mới <= block đang có → từ chối ghi đè.
     */
    @Column(name = "last_synced_block", nullable = false)
    private Long lastSyncedBlock;

    /**
     * Hash của block đã sync.
     *
     * <p>
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
     * @param newBlock      block number mới từ Cardano
     * @param newBlockHash  hash của block mới
     * @return true nếu block mới cao hơn hoặc khác hash (reorg)
     */
    public boolean isBlockNewerOrReorg(long newBlock, String newBlockHash) {
        if (newBlock > lastSyncedBlock) {
            return true; // Block cao hơn → sync bình thường
        }
        if (newBlock == lastSyncedBlock && !blockHash.equals(newBlockHash)) {
            return true; // Cùng height nhưng hash khác → reorg
        }
        return false; // Block thấp hơn hoặc trùng hash → bỏ qua
    }
}
