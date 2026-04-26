package com.magiclamp.phoenixkey_db.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.magiclamp.phoenixkey_db.common.UuidGenerator;
import com.magiclamp.phoenixkey_db.domain.AuthorizedKey;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityServiceImpl implements IdentityService {

    @SuppressWarnings("unused") // wired in Phase C khi register() có body thật
    private final UserRepository userRepository;
    private final AuthorizedKeyRepository authorizedKeyRepository;
    private final OnchainTaadStateCacheRepository taadCacheRepository;
    @SuppressWarnings("unused")
    private final ActivityLogService activityLogService;
    @SuppressWarnings("unused")
    private final UuidGenerator uuidGenerator;
    // Phase B sẽ wire SignatureService + CardanoService vào đây.

    // ──────────────────────────────────────────────────────────────
    // Register
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public IdentityRegisterResponse register(IdentityRegisterRequest request) {
        // TODO[Phase C]: implement đầy đủ sau khi Phase B có CardanoService + SignatureService.
        // Flow:
        //   1. signatureService.verifyEcdsa(publicKeyHex, "PHOENIXKEY_GENESIS:" + publicKeyHex, addedBySignature)
        //   2. tx = cardanoService.createDID(publicKeyHex)
        //   3. INSERT users + authorized_keys với userDid = tx.did()
        //   4. activityLogService.log(USER_REGISTERED)
        //   5. return new IdentityRegisterResponse(userId, userDid, tx.txHash())
        log.warn("IdentityService.register() called but Phase B/C not done yet — request={}", request);
        throw new UnsupportedOperationException(
                "register() not implemented in Phase A — see Phase B (Cardano integration) + Phase C");
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
