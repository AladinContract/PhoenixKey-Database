package com.magiclamp.phoenixkey_db.service.impl;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.magiclamp.phoenixkey_db.common.UuidGenerator;
import com.magiclamp.phoenixkey_db.crypto.BlindIndexService;
import com.magiclamp.phoenixkey_db.domain.AuthMethod;
import com.magiclamp.phoenixkey_db.domain.AuthorizedKey;
import com.magiclamp.phoenixkey_db.domain.User;
import com.magiclamp.phoenixkey_db.dto.request.IdentityRegisterRequest;
import com.magiclamp.phoenixkey_db.dto.request.UserDidUpdateRequest;
import com.magiclamp.phoenixkey_db.dto.response.IdentityPubkeyResponse;
import com.magiclamp.phoenixkey_db.dto.response.IdentityRegisterResponse;
import com.magiclamp.phoenixkey_db.dto.response.IdentityStatusResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.AuthMethodRepository;
import com.magiclamp.phoenixkey_db.repository.AuthorizedKeyRepository;
import com.magiclamp.phoenixkey_db.repository.OnchainTaadStateCacheRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import com.magiclamp.phoenixkey_db.service.ActivityLogService;
import com.magiclamp.phoenixkey_db.service.IdentityService;
import com.magiclamp.phoenixkey_db.service.InvitationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityServiceImpl implements IdentityService {

    private final UserRepository userRepository;
    private final AuthMethodRepository authMethodRepository;
    private final AuthorizedKeyRepository authorizedKeyRepository;
    private final OnchainTaadStateCacheRepository taadCacheRepository;
    private final BlindIndexService blindIndexService;
    private final ActivityLogService activityLogService;
    private final UuidGenerator uuidGenerator;
    private final InvitationService invitationService;

    // ──────────────────────────────────────────────────────────────
    // Register
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public IdentityRegisterResponse register(IdentityRegisterRequest request) {
        String credential = request.credential().toLowerCase().trim();
        String blindHash = blindIndexService.hash(credential);

        // Check: credential đã đăng ký chưa?
        if (authMethodRepository.existsByBlindIndexHash(blindHash)) {
            throw new AppException(ErrorCode.AUTH_METHOD_ALREADY_EXISTS);
        }

        // Tạo UUIDv7 cho user
        UUID userId = uuidGenerator.create();

        // Tạo User (user_did sẽ được NestJS mint trên Cardano rồi update lại)
        // Không set version — để @PrePersist xử lý → Spring Data JPA gọi persist()
        // thay vì merge(), giữ entity managed trong persistence context.
        User user = User.builder()
                .id(userId)
                .userDid("pending") // NestJS sẽ update sau khi mint DID
                .createdAt(OffsetDateTime.now())
                .build();
        user = userRepository.save(user);

        // Tạo AuthMethod
        AuthMethod authMethod = AuthMethod.builder()
                .id(uuidGenerator.create())
                .user(user)
                .provider(request.provider())
                .blindIndexHash(blindHash)
                .pepperVersion(blindIndexService.getCurrentPepperVersion())
                .isVerified(true) // Đã verify OTP trước đó
                .addedAt(OffsetDateTime.now())
                .build();
        authMethodRepository.save(authMethod);

        // Tạo AuthorizedKey (owner key)
        AuthorizedKey ownerKey = AuthorizedKey.builder()
                .id(uuidGenerator.create())
                .userDid(user.getUserDid())
                .publicKeyHex(request.publicKeyHex())
                .keyOrigin(request.keyOrigin())
                .keyRole(request.keyRole())
                .addedBySignature(request.addedBySignature())
                .status("active")
                .createdAt(OffsetDateTime.now())
                .build();
        authorizedKeyRepository.save(ownerKey);

        // [V1.5] Auto-resolve pending invitations
        // Khi user đăng ký bằng SĐT/Email đã được mời → auto-add guardian
        invitationService.resolveOnRegistration(blindHash, userId);

        log.info("User registered: id={}, blind_hash={}, pubkey={}",
                userId, blindHash, request.publicKeyHex());

        activityLogService.log(userId, ActivityLogService.ACTION_USER_REGISTERED,
                Map.of("provider", request.provider().name()));

        return new IdentityRegisterResponse(userId.toString(), "pending");
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
    // Update DID
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void updateUserDid(UserDidUpdateRequest request) {
        // Tìm user theo ID
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Check: userDid mới đã được gán cho user khác chưa?
        if (!user.getUserDid().equals(request.userDid())
                && userRepository.existsByUserDid(request.userDid())) {
            throw new AppException(ErrorCode.USER_DID_ALREADY_EXISTS);
        }

        String oldDid = user.getUserDid();
        user.setUserDid(request.userDid());
        userRepository.save(user);

        log.info("UserDid updated: userId={}, oldDid={}, newDid={}",
                request.userId(), oldDid, request.userDid());

        activityLogService.log(
                user.getId(),
                ActivityLogService.ACTION_DID_UPDATED,
                Map.of("old_did", oldDid,
                        "new_did", request.userDid()));
    }
}
