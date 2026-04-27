package com.magiclamp.phoenixkey_db.service.impl;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.magiclamp.phoenixkey_db.common.UuidGenerator;
import com.magiclamp.phoenixkey_db.domain.AuthorizedKey;
import com.magiclamp.phoenixkey_db.domain.User;
import com.magiclamp.phoenixkey_db.dto.request.KeyAuthorizeRequest;
import com.magiclamp.phoenixkey_db.dto.request.KeyRevokeRequest;
import com.magiclamp.phoenixkey_db.dto.request.KeyRotateRequest;
import com.magiclamp.phoenixkey_db.dto.response.KeyAuthorizeResponse;
import com.magiclamp.phoenixkey_db.dto.response.KeyRotationResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.AuthorizedKeyRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import com.magiclamp.phoenixkey_db.service.ActivityLogService;
import com.magiclamp.phoenixkey_db.service.KeyService;
import com.magiclamp.phoenixkey_db.service.NonceService;
import com.magiclamp.phoenixkey_db.service.cardano.CardanoService;
import com.magiclamp.phoenixkey_db.service.cardano.dto.TxResult;
import com.magiclamp.phoenixkey_db.service.crypto.SignatureService;

import java.nio.charset.StandardCharsets;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeyServiceImpl implements KeyService {

    private final AuthorizedKeyRepository authorizedKeyRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final UuidGenerator uuidGenerator;
    private final NonceService nonceService;
    private final SignatureService signatureService;
    private final CardanoService cardanoService;

    /** Prefix message ký rotate — phân biệt với GENESIS để chống replay cross-flow. */
    private static final String ROTATE_PREFIX = "PHOENIXKEY_ROTATE:";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_REVOKED = "revoked";

    // ──────────────────────────────────────────────────────────────
    // Authorize
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public KeyAuthorizeResponse authorize(KeyAuthorizeRequest request) {
        // [V1.5] Validate + consume nonce — chống replay attack
        nonceService.validateAndConsume(request.nonce(), request.userDid(), Duration.ofMinutes(5));

        // Verify user tồn tại
        User user = userRepository.findByUserDid(request.userDid())
                .orElseThrow(() -> new AppException(ErrorCode.USER_DID_NOT_FOUND));

        // Check: key chưa được authorized cho user này?
        // Chỉ check active key của chính user — không block key đã revoked
        // (revoked key có thể được authorize lại sau khi revoke)
        if (authorizedKeyRepository.existsByUserDidAndPublicKeyHexAndStatus(
                request.userDid(), request.publicKeyHex(), STATUS_ACTIVE)) {
            throw new AppException(ErrorCode.KEY_ALREADY_AUTHORIZED);
        }

        // Insert authorized_key
        AuthorizedKey key = AuthorizedKey.builder()
                .id(uuidGenerator.create())
                .userDid(request.userDid())
                .publicKeyHex(request.publicKeyHex())
                .keyOrigin(request.keyOrigin())
                .keyRole(request.keyRole())
                .addedBySignature(request.addedBySignature())
                .status(STATUS_ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();
        authorizedKeyRepository.save(key);

        log.info("Key authorized: userDid={}, pubkey={}, role={}, origin={}",
                request.userDid(), request.publicKeyHex(), request.keyRole(), request.keyOrigin());

        activityLogService.log(user.getId(), ActivityLogService.ACTION_KEY_AUTHORIZED,
                Map.of("public_key_hex", request.publicKeyHex(),
                        "key_role", request.keyRole(),
                        "key_origin", request.keyOrigin().name()));

        return new KeyAuthorizeResponse(key.getId().toString());
    }

    // ──────────────────────────────────────────────────────────────
    // Revoke
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void revoke(KeyRevokeRequest request) {
        // [V1.5] Validate + consume nonce — chống replay attack
        nonceService.validateAndConsume(request.nonce(), request.userDid(), Duration.ofMinutes(5));

        // Verify user tồn tại
        User user = userRepository.findByUserDid(request.userDid())
                .orElseThrow(() -> new AppException(ErrorCode.USER_DID_NOT_FOUND));

        // Verify: key có tồn tại?
        AuthorizedKey key = authorizedKeyRepository.findByPublicKeyHex(request.publicKeyHex())
                .orElseThrow(() -> new AppException(ErrorCode.KEY_NOT_FOUND));

        // Verify: key thuộc user này?
        if (!key.getUserDid().equals(request.userDid())) {
            throw new AppException(ErrorCode.KEY_NOT_FOUND);
        }

        // Verify: đã revoked?
        if (!key.isActive()) {
            throw new AppException(ErrorCode.KEY_ALREADY_REVOKED);
        }

        // Revoke
        key.setStatus(STATUS_REVOKED);
        authorizedKeyRepository.save(key);

        log.info("Key revoked: userDid={}, pubkey={}",
                request.userDid(), request.publicKeyHex());

        activityLogService.log(user.getId(), ActivityLogService.ACTION_KEY_REVOKED,
                Map.of("public_key_hex", request.publicKeyHex()));
    }

    // ──────────────────────────────────────────────────────────────
    // Rotate (Spec §11)
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public KeyRotationResponse rotate(KeyRotateRequest request) {
        // 1. User tồn tại + có owner key đang active
        User user = userRepository.findByUserDid(request.userDid())
                .orElseThrow(() -> new AppException(ErrorCode.USER_DID_NOT_FOUND));

        AuthorizedKey oldKey = authorizedKeyRepository.findOwnerByUserDid(request.userDid())
                .orElseThrow(() -> new AppException(ErrorCode.KEY_NOT_FOUND,
                        "no active owner key for " + request.userDid()));

        // 2. Verify chữ ký từ KEY CŨ trên (newPublicKeyHex + nonce). Đảm bảo
        //    người gọi sở hữu private key cũ — chống attacker chiếm tài khoản.
        byte[] message = (ROTATE_PREFIX + request.newPublicKeyHex() + ":" + request.nonce())
                .getBytes(StandardCharsets.UTF_8);
        byte[] signature = hexToBytes(request.oldKeySignature());
        if (!signatureService.verifyEcdsa(oldKey.getPublicKeyHex(), message, signature)) {
            log.warn("Rotate signature invalid: userDid={}, old key={}",
                    request.userDid(), oldKey.getPublicKeyHex());
            throw new AppException(ErrorCode.SIGNATURE_INVALID,
                    "Old key signature verification failed");
        }

        // 3. Consume nonce — chống replay
        nonceService.validateAndConsume(request.nonce(), request.userDid(), Duration.ofMinutes(5));

        // 4. Submit Cardano updateDID tx — consume UTxO cũ + tạo UTxO mới datum
        //    chứa newPublicKeyHex.
        String prevTxHash = extractTxHashFromDid(request.userDid());
        TxResult tx = cardanoService.updateDID(request.newPublicKeyHex(), prevTxHash, null);

        // 5. DB: revoke key cũ + insert key mới active
        oldKey.setStatus(STATUS_REVOKED);
        authorizedKeyRepository.save(oldKey);

        AuthorizedKey newKey = AuthorizedKey.builder()
                .id(uuidGenerator.create())
                .userDid(request.userDid())
                .publicKeyHex(request.newPublicKeyHex())
                .keyOrigin(request.keyOrigin())
                .keyRole("owner")
                .addedBySignature(request.oldKeySignature())
                .status(STATUS_ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();
        authorizedKeyRepository.save(newKey);

        // 6. Reset seedExportedAt nếu đang set (spec §9.5 — rotate xong, banner
        //    cảnh báo Seed Export tự ẩn). Phase E sẽ wire sau.

        log.info("Key rotated: userDid={}, oldKey={}, newKey={}, txHash={}",
                request.userDid(), oldKey.getPublicKeyHex(),
                request.newPublicKeyHex(), tx.txHash());

        activityLogService.log(user.getId(), ActivityLogService.ACTION_KEY_ROTATED,
                Map.of("old_pubkey", oldKey.getPublicKeyHex(),
                        "new_pubkey", request.newPublicKeyHex(),
                        "tx_hash", tx.txHash()));

        return new KeyRotationResponse(tx.txHash(), newKey.getId().toString());
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    /** Extract Cardano tx hash từ DID string {@code did:cardano:<network>:<txHash>}. */
    private static String extractTxHashFromDid(String did) {
        String[] parts = did.split(":");
        if (parts.length != 4 || !"did".equals(parts[0]) || !"cardano".equals(parts[1])) {
            throw new AppException(ErrorCode.USER_DID_NOT_FOUND,
                    "Invalid DID format: " + did);
        }
        return parts[3];
    }

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
}