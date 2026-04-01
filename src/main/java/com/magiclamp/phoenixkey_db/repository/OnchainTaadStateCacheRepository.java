package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.OnchainTaadStateCache;
import com.magiclamp.phoenixkey_db.domain.TaadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho
 * {@link com.magiclamp.phoenixkey_db.domain.OnchainTaadStateCache}.
 *
 * <p>
 * <b>Nguyên tắc quan trọng:</b>
 * Bảng này chỉ được GHI bởi Indexer Worker.
 * Không nhận lệnh trực tiếp từ App.
 *
 * <p>
 * Tất cả update đều phải qua optimistic locking check
 * ({@code last_synced_block < new_block}).
 */
@Repository
public interface OnchainTaadStateCacheRepository extends JpaRepository<OnchainTaadStateCache, String> {

    // ──────────────────────────────────────────────────────────────
    // Đọc (App sử dụng — chỉ đọc thôi, không ghi)
    // ──────────────────────────────────────────────────────────────

    /**
     * Tìm cache state của một user.
     *
     * @param userDid DID string
     * @return Optional chứa cache state
     */
    Optional<OnchainTaadStateCache> findByUserDid(String userDid);

    /**
     * Kiểm tra user có đang trong trạng thái khôi phục không.
     *
     * @param userDid DID string
     * @return true nếu status = RECOVERING
     */
    @Query("SELECT CASE WHEN c.status = 'RECOVERING' THEN true ELSE false END " +
            "FROM OnchainTaadStateCache c WHERE c.userDid = :userDid")
    boolean isRecovering(@Param("userDid") String userDid);

    // ──────────────────────────────────────────────────────────────
    // Tra cứu theo trạng thái (Indexer Worker dùng)
    // ──────────────────────────────────────────────────────────────

    /**
     * Tìm tất cả user đang ở trạng thái khôi phục.
     * Dùng để thông báo cho guardian / monitoring.
     *
     * @return danh sách cache đang recovering
     */
    List<OnchainTaadStateCache> findByStatus(TaadStatus status);

    /**
     * Tìm tất cả user đang active.
     *
     * @return danh sách cache active
     */
    @Query("SELECT c FROM OnchainTaadStateCache c WHERE c.status = com.magiclamp.phoenixkey_db.domain.TaadStatus.ACTIVE")
    List<OnchainTaadStateCache> findByStatusActive();

    // ──────────────────────────────────────────────────────────────
    // Batch sync (Indexer Worker)
    // ──────────────────────────────────────────────────────────────

    /**
     * Tìm các dòng cần sync (cache cũ hơn block height).
     * Dùng khi Indexer Worker quét batch.
     *
     * @param blockHeight block height tối thiểu
     * @return danh sách cache cần sync
     */
    @Query("SELECT c FROM OnchainTaadStateCache c WHERE c.lastSyncedBlock < :blockHeight")
    List<OnchainTaadStateCache> findStaleCache(@Param("blockHeight") long blockHeight);

    /**
     * Tìm các dòng có block hash khác với hash hiện tại (reorg detected).
     *
     * @param blockHeight  block height
     * @param expectedHash hash mong đợi
     * @return danh sách bị reorg
     */
    @Query("SELECT c FROM OnchainTaadStateCache c " +
            "WHERE c.lastSyncedBlock = :blockHeight AND c.blockHash <> :expectedHash")
    List<OnchainTaadStateCache> findReorgedCache(
            @Param("blockHeight") long blockHeight,
            @Param("expectedHash") String expectedHash);

    // ──────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────

    /**
     * Xóa cache của user (dùng khi reorg detected hoặc user bị xóa).
     *
     * @param userDid DID string
     */
    @Modifying
    @Query("DELETE FROM OnchainTaadStateCache c WHERE c.userDid = :userDid")
    void deleteByUserDid(@Param("userDid") String userDid);

    /**
     * Đếm số user đang recovering.
     * Dùng cho monitoring dashboard.
     *
     * @return số user recovering
     */
    long countByStatus(TaadStatus status);
}
