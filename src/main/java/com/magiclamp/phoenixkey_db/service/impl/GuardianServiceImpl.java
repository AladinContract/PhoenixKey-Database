package com.magiclamp.phoenixkey_db.service.impl;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.magiclamp.phoenixkey_db.common.UuidGenerator;
import com.magiclamp.phoenixkey_db.domain.Guardian;
import com.magiclamp.phoenixkey_db.domain.User;
import com.magiclamp.phoenixkey_db.dto.request.GuardianAddRequest;
import com.magiclamp.phoenixkey_db.dto.request.GuardianRemoveRequest;
import com.magiclamp.phoenixkey_db.dto.response.GuardianRemoveResponse;
import com.magiclamp.phoenixkey_db.dto.response.GuardianAddResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.GuardianRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import com.magiclamp.phoenixkey_db.service.ActivityLogService;
import com.magiclamp.phoenixkey_db.service.GuardianService;
import com.magiclamp.phoenixkey_db.service.NonceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuardianServiceImpl implements GuardianService {

    private final GuardianRepository guardianRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final UuidGenerator uuidGenerator;
    private final NonceService nonceService;

    // ──────────────────────────────────────────────────────────────
    // Add Guardian
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public GuardianAddResponse addGuardian(GuardianAddRequest request) {
        // [V1.5] Validate + consume nonce — chống replay attack
        nonceService.validateAndConsume(request.nonce(), request.userDid(), Duration.ofMinutes(5));

        // Verify user tồn tại
        User user = userRepository.findByUserDid(request.userDid())
                .orElseThrow(() -> new AppException(ErrorCode.USER_DID_NOT_FOUND));

        // Check: guardian đã tồn tại?
        if (guardianRepository.existsByUserIdAndGuardianDidAndStatus(
                user.getId(), request.guardianDid(), "active")) {
            throw new AppException(ErrorCode.GUARDIAN_ALREADY_EXISTS);
        }

        // Insert guardian
        Guardian guardian = Guardian.builder()
                .id(uuidGenerator.create())
                .user(user)
                .guardianDid(request.guardianDid())
                .proofSignature(request.proofSignature())
                .status("active")
                .createdAt(OffsetDateTime.now())
                .build();
        guardianRepository.save(guardian);

        long newCount = guardianRepository.countActiveByUserId(user.getId());

        log.info("Guardian added: userDid={}, guardianDid={}, count={}",
                request.userDid(), request.guardianDid(), newCount);

        activityLogService.log(
                user.getId(),
                ActivityLogService.ACTION_GUARDIAN_ADDED,
                Map.of("guardian_did", request.guardianDid()));

        return new GuardianAddResponse((int) newCount);
    }

    // ──────────────────────────────────────────────────────────────
    // Remove Guardian
    // ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public GuardianRemoveResponse removeGuardian(GuardianRemoveRequest request) {
        // [V1.5] Validate + consume nonce — chống replay attack
        nonceService.validateAndConsume(request.nonce(), request.userDid(), Duration.ofMinutes(5));

        // Verify user tồn tại
        User user = userRepository.findByUserDid(request.userDid())
                .orElseThrow(() -> new AppException(ErrorCode.USER_DID_NOT_FOUND));

        // Revoke guardian (chỉ revoke nếu đang active)
        int updated = guardianRepository.revokeByUserIdAndGuardianDid(
                user.getId(), request.guardianDid());

        if (updated == 0) {
            throw new AppException(ErrorCode.GUARDIAN_NOT_FOUND);
        }

        long remainingCount = guardianRepository.countActiveByUserId(user.getId());

        log.info("Guardian removed: userDid={}, guardianDid={}, remaining={}",
                request.userDid(), request.guardianDid(), remainingCount);

        activityLogService.log(
                user.getId(),
                ActivityLogService.ACTION_GUARDIAN_REMOVED,
                Map.of("guardian_did", request.guardianDid()));

        return new GuardianRemoveResponse((int) remainingCount);
    }
}