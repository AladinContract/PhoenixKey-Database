package com.magiclamp.phoenixkey_db.service.impl;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.magiclamp.phoenixkey_db.common.UuidGenerator;
import com.magiclamp.phoenixkey_db.domain.AuthorizedKey;
import com.magiclamp.phoenixkey_db.domain.User;
import com.magiclamp.phoenixkey_db.dto.request.KeyAuthorizeRequest;
import com.magiclamp.phoenixkey_db.dto.request.KeyRevokeRequest;
import com.magiclamp.phoenixkey_db.dto.response.KeyAuthorizeResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.AuthorizedKeyRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import com.magiclamp.phoenixkey_db.service.ActivityLogService;
import com.magiclamp.phoenixkey_db.service.KeyService;

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

    // ──────────────────────────────────────────────────────────────
    // Authorize
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public KeyAuthorizeResponse authorize(KeyAuthorizeRequest request) {
        // Verify user tồn tại
        User user = userRepository.findByUserDid(request.userDid())
                .orElseThrow(() -> new AppException(ErrorCode.USER_DID_NOT_FOUND));

        // Check: key chưa được authorized?
        if (authorizedKeyRepository.existsByUserUserDidAndPublicKeyHexAndStatus(
                request.userDid(), request.publicKeyHex(), "active")) {
            throw new AppException(ErrorCode.KEY_ALREADY_AUTHORIZED);
        }

        // Check: key đã revoked trước đó?
        if (authorizedKeyRepository.existsByPublicKeyHex(request.publicKeyHex())) {
            // Key đã tồn tại → không cho phép insert lại
            throw new AppException(ErrorCode.KEY_ALREADY_AUTHORIZED);
        }

        // Insert authorized_key
        AuthorizedKey key = AuthorizedKey.builder()
                .id(uuidGenerator.create())
                .user(user)
                .publicKeyHex(request.publicKeyHex())
                .keyRole(request.keyRole())
                .addedBySignature(request.addedBySignature())
                .status("active")
                .createdAt(OffsetDateTime.now())
                .build();
        authorizedKeyRepository.save(key);

        log.info("Key authorized: userDid={}, pubkey={}, role={}",
                request.userDid(), request.publicKeyHex(), request.keyRole());

        activityLogService.log(user.getId(), ActivityLogService.ACTION_KEY_AUTHORIZED,
                Map.of("public_key_hex", request.publicKeyHex(),
                        "key_role", request.keyRole()));

        return new KeyAuthorizeResponse(key.getId().toString());
    }

    // ──────────────────────────────────────────────────────────────
    // Revoke
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void revoke(KeyRevokeRequest request) {
        // Verify user tồn tại
        User user = userRepository.findByUserDid(request.userDid())
                .orElseThrow(() -> new AppException(ErrorCode.USER_DID_NOT_FOUND));

        // Verify: key có tồn tại?
        AuthorizedKey key = authorizedKeyRepository.findByPublicKeyHex(request.publicKeyHex())
                .orElseThrow(() -> new AppException(ErrorCode.KEY_NOT_FOUND));

        // Verify: key thuộc user này?
        if (!key.getUser().getUserDid().equals(request.userDid())) {
            throw new AppException(ErrorCode.KEY_NOT_FOUND);
        }

        // Verify: đã revoked?
        if (!key.isActive()) {
            throw new AppException(ErrorCode.KEY_ALREADY_REVOKED);
        }

        // Revoke
        key.setStatus("revoked");
        authorizedKeyRepository.save(key);

        log.info("Key revoked: userDid={}, pubkey={}",
                request.userDid(), request.publicKeyHex());

        activityLogService.log(user.getId(), ActivityLogService.ACTION_KEY_REVOKED,
                Map.of("public_key_hex", request.publicKeyHex()));
    }
}
