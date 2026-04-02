package com.magiclamp.phoenixkey_db.service.impl;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.magiclamp.phoenixkey_db.domain.OnchainTaadStateCache;
import com.magiclamp.phoenixkey_db.dto.request.SyncTaadRequest;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.OnchainTaadStateCacheRepository;
import com.magiclamp.phoenixkey_db.service.ActivityLogService;
import com.magiclamp.phoenixkey_db.service.IndexerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexerServiceImpl implements IndexerService {

    private final OnchainTaadStateCacheRepository taadCacheRepository;
    private final ActivityLogService activityLogService;

    // ──────────────────────────────────────────────────────────────
    // Sync TAAD
    // ──────────────────────────────────────────────────────────────

    /**
     * Indexer Worker gọi khi thấy thay đổi trạng thái TAAD trên Blockchain.
     *
     * <p>
     * <b>Lưu ý:</b> Recovery approval (guardian ký) được xử lý trực tiếp trên
     * Smart Contract Cardano. Indexer chỉ sync kết quả cuối cùng (DID Controller đã đổi).
     */
    @Override
    @Transactional
    public void syncTaad(SyncTaadRequest request) {
        String userDid = request.userDid();

        var existingCache = taadCacheRepository.findById(userDid);

        if (existingCache.isEmpty()) {
            insertCache(request);
            return;
        }

        OnchainTaadStateCache cache = existingCache.get();

        // Reorg detected: cùng block height nhưng hash khác
        if (Objects.equals(cache.getLastSyncedBlock(), request.lastSyncedBlock())
                && !cache.getBlockHash().equals(request.blockHash())) {
            log.warn("Reorg detected for userDid={}, block={}, oldHash={}, newHash={}",
                    userDid, request.lastSyncedBlock(),
                    cache.getBlockHash(), request.blockHash());
            taadCacheRepository.deleteById(userDid);
            insertCache(request);
            return;
        }

        // Stale update: block mới <= block đang có
        if (cache.getLastSyncedBlock() >= request.lastSyncedBlock()) {
            log.debug("Stale update skipped for userDid={}, existingBlock={}, newBlock={}",
                    userDid, cache.getLastSyncedBlock(), request.lastSyncedBlock());
            throw new AppException(ErrorCode.TAAD_STATE_STALE);
        }

        // Valid update: cập nhật cache
        cache.setCurrentControllerPkh(request.currentControllerPkh());
        cache.setSequence(request.sequence());
        cache.setStatus(request.status());
        cache.setRecoveryDeadline(parseDeadline(request.recoveryDeadline()));
        cache.setLastSyncedBlock(request.lastSyncedBlock());
        cache.setBlockHash(request.blockHash());
        cache.setUpdatedAt(OffsetDateTime.now());
        taadCacheRepository.save(cache);

        log.info("TAAD synced: userDid={}, status={}, block={}",
                userDid, request.status(), request.lastSyncedBlock());

        activityLogService.log(
                ActivityLogService.ACTION_TAAD_SYNCED,
                Map.of(
                        "user_did", userDid,
                        "status", request.status().name(),
                        "last_synced_block", String.valueOf(request.lastSyncedBlock())));
    }

    private void insertCache(SyncTaadRequest request) {
        OnchainTaadStateCache cache = OnchainTaadStateCache.builder()
                .userDid(request.userDid())
                .currentControllerPkh(request.currentControllerPkh())
                .sequence(request.sequence())
                .status(request.status())
                .recoveryDeadline(parseDeadline(request.recoveryDeadline()))
                .lastSyncedBlock(request.lastSyncedBlock())
                .blockHash(request.blockHash())
                .updatedAt(OffsetDateTime.now())
                .build();
        taadCacheRepository.save(cache);

        log.info("TAAD cache created: userDid={}, status={}, block={}",
                request.userDid(), request.status(), request.lastSyncedBlock());
    }

    private OffsetDateTime parseDeadline(String deadline) {
        if (deadline == null || deadline.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(deadline);
    }
}
