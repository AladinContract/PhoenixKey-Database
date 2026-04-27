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
import com.magiclamp.phoenixkey_db.service.crypto.SignatureService;

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
    private final SignatureService signatureService;

    // ──────────────────────────────────────────────────────────────
    // Register
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public IdentityRegisterResponse register(IdentityRegisterRequest request) {
        // Verify Genesis signature: chứng minh user thực sự sở hữu private key
        // tương ứng với publicKeyHex (chống user "đăng ký" key của người khác).
        // Chữ ký DER trên message "PHOENIXKEY_GENESIS:" + publicKeyHex.
        byte[] signature = hexToBytes(request.addedBySignature());
        if (!signatureService.verifyGenesis(request.publicKeyHex(), signature)) {
            log.warn("Genesis signature invalid for publicKeyHex={}",
                    truncate(request.publicKeyHex()));
            throw new AppException(ErrorCode.SIGNATURE_INVALID,
                    "Genesis signature verification failed");
        }

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

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private static byte[] hexToBytes(String hex) {
        if (hex == null) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "signature is null");
        }
        String s = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if (s.length() % 2 != 0) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID,
                    "signature hex length must be even, got " + s.length());
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            try {
                out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
            } catch (NumberFormatException e) {
                throw new AppException(ErrorCode.SIGNATURE_INVALID,
                        "signature contains non-hex chars at index " + (i * 2));
            }
        }
        return out;
    }

    private static String truncate(String s) {
        if (s == null || s.length() <= 12) {
            return s;
        }
        return s.substring(0, 12) + "...";
    }
}
