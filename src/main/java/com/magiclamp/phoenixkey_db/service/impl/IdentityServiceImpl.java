package com.magiclamp.phoenixkey_db.service.impl;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.magiclamp.phoenixkey_db.common.UuidGenerator;
import com.magiclamp.phoenixkey_db.domain.AuthorizedKey;
import com.magiclamp.phoenixkey_db.domain.User;
import com.magiclamp.phoenixkey_db.dto.request.IdentityRegisterRequest;
import com.magiclamp.phoenixkey_db.dto.response.IdentityPubkeyResponse;
import com.magiclamp.phoenixkey_db.dto.response.IdentityRegisterResponse;
import com.magiclamp.phoenixkey_db.dto.response.IdentityStatusResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.AuthorizedKeyRepository;
import com.magiclamp.phoenixkey_db.repository.OnchainTaadStateCacheRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import com.magiclamp.phoenixkey_db.service.ActivityLogService;
import com.magiclamp.phoenixkey_db.service.IdentityService;
import com.magiclamp.phoenixkey_db.service.cardano.CardanoService;
import com.magiclamp.phoenixkey_db.service.cardano.dto.TxResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityServiceImpl implements IdentityService {

    private final UserRepository userRepository;
    private final AuthorizedKeyRepository authorizedKeyRepository;
    private final OnchainTaadStateCacheRepository taadCacheRepository;
    private final ActivityLogService activityLogService;
    private final UuidGenerator uuidGenerator;
    private final CardanoService cardanoService;
    // Phase B.4 sẽ wire SignatureService — verify Genesis signature trước createDID.

    // ──────────────────────────────────────────────────────────────
    // Register
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public IdentityRegisterResponse register(IdentityRegisterRequest request) {
        // TODO[Phase B.4]: verify addedBySignature qua SignatureService trước khi
        // chi ADA submit tx. Hiện tại trust client → SECURITY HOLE, chỉ dùng để
        // smoke-test Cardano integration.
        log.warn("Skipping Genesis signature verify (Phase B.4 chưa làm)");

        // Publish DID lên Cardano. CardanoService throw AppException(CARDANO_TX_FAILED)
        // nếu submit/confirm fail — không rollback DB vì chưa insert.
        TxResult tx = cardanoService.createDID(request.publicKeyHex());
        String userDid = tx.did();

        // Insert user + owner key trong cùng transaction.
        UUID userId = uuidGenerator.create();
        User user = User.builder()
                .id(userId)
                .userDid(userDid)
                .createdAt(OffsetDateTime.now())
                .build();
        userRepository.save(user);

        AuthorizedKey ownerKey = AuthorizedKey.builder()
                .id(uuidGenerator.create())
                .userDid(userDid)
                .publicKeyHex(request.publicKeyHex())
                .keyOrigin(request.keyOrigin())
                .keyRole(request.keyRole())
                .addedBySignature(request.addedBySignature())
                .status("active")
                .createdAt(OffsetDateTime.now())
                .build();
        authorizedKeyRepository.save(ownerKey);

        activityLogService.log(userId, ActivityLogService.ACTION_USER_REGISTERED,
                Map.of("did", userDid, "tx_hash", tx.txHash()));

        return new IdentityRegisterResponse(userId.toString(), userDid, tx.txHash());
    }

    // ──────────────────────────────────────────────────────────────
    // Get Pubkey
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public IdentityPubkeyResponse getPubkey(String userDid) {
        AuthorizedKey key = authorizedKeyRepository.findOwnerByUserDid(userDid)
                .orElseThrow(() -> new AppException(ErrorCode.KEY_NOT_FOUND));

        return new IdentityPubkeyResponse(key.getPublicKeyHex(), key.getKeyRole());
    }

    // ──────────────────────────────────────────────────────────────
    // Get Status
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public IdentityStatusResponse getStatus(String userDid) {
        // Check user tồn tại
        if (!userRepository.existsByUserDid(userDid)) {
            throw new AppException(ErrorCode.USER_DID_NOT_FOUND);
        }

        return taadCacheRepository.findByUserDid(userDid)
                .map(cache -> new IdentityStatusResponse(
                        cache.getStatus(),
                        cache.getCurrentControllerPkh(),
                        cache.getSequence(),
                        cache.getRecoveryDeadline() != null
                                ? cache.getRecoveryDeadline().toString()
                                : null))
                .orElseThrow(() -> new AppException(ErrorCode.TAAD_STATE_NOT_FOUND));
    }
}
