package com.magiclamp.phoenixkey_db.service.impl;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.magiclamp.phoenixkey_db.common.UuidGenerator;
import com.magiclamp.phoenixkey_db.domain.Guardian;
import com.magiclamp.phoenixkey_db.domain.PendingInvitation;
import com.magiclamp.phoenixkey_db.domain.User;
import com.magiclamp.phoenixkey_db.repository.GuardianRepository;
import com.magiclamp.phoenixkey_db.repository.PendingInvitationRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import com.magiclamp.phoenixkey_db.service.InvitationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationServiceImpl implements InvitationService {

    private final PendingInvitationRepository invitationRepository;
    private final GuardianRepository guardianRepository;
    private final UserRepository userRepository;
    private final UuidGenerator uuidGenerator;

    @Override
    @Transactional
    public void createInvitation(UUID inviterUserId, String inviteeBlindHash, String inviteType, Duration ttl) {
        // Load full inviter to get userDid
        User inviter = userRepository.findById(inviterUserId)
                .orElseThrow(() -> new IllegalArgumentException("Inviter user not found: " + inviterUserId));

        PendingInvitation invitation = PendingInvitation.builder()
                .id(uuidGenerator.create())
                .inviterUser(inviter)
                .inviteeBlindHash(inviteeBlindHash)
                .inviteType(inviteType)
                .expiresAt(OffsetDateTime.now().plus(ttl))
                .status("pending")
                .createdAt(OffsetDateTime.now())
                .build();
        invitationRepository.save(invitation);

        log.info("Invitation created: inviterDid={}, inviteeBlindHash={}, type={}",
                inviter.getUserDid(), inviteeBlindHash, inviteType);
    }

    @Override
    @Transactional
    public void resolveOnRegistration(String inviteeBlindHash, UUID inviteeUserId) {
        List<PendingInvitation> pending = invitationRepository
                .findByInviteeBlindHashAndStatus(inviteeBlindHash, "pending");

        if (pending.isEmpty()) {
            log.debug("No pending invitations for blindHash={}", inviteeBlindHash);
            return;
        }

        for (PendingInvitation inv : pending) {
            if ("guardian".equals(inv.getInviteType())) {
                // Auto-add guardian: invitee = guardian của inviter
                String inviterDid = inv.getInviterUser().getUserDid();
                String inviteeDid = userRepository.findById(inviteeUserId)
                        .map(User::getUserDid)
                        .orElseThrow();

                // Check chưa tồn tại guardian
                if (!guardianRepository.existsByUserIdAndGuardianDidAndStatus(
                        inviteeUserId, inviterDid, "active")) {
                    Guardian guardian = Guardian.builder()
                            .id(uuidGenerator.create())
                            .user(userRepository.getReferenceById(inviteeUserId))
                            .guardianDid(inviterDid)
                            .proofSignature("auto_resolved:" + inv.getId())
                            .status("active")
                            .createdAt(OffsetDateTime.now())
                            .build();
                    guardianRepository.save(guardian);
                    log.info("Guardian auto-resolved: inviteeDid={}, guardianDid={}",
                            inviteeDid, inviterDid);
                }
            }
            // Mark resolved
            inv.setStatus("resolved");
            invitationRepository.save(inv);
        }

        log.info("Resolved {} pending invitations for blindHash={}", pending.size(), inviteeBlindHash);
    }

    @Override
    @Transactional
    public int markExpired() {
        int updated = invitationRepository.markExpired(OffsetDateTime.now());
        if (updated > 0) {
            log.info("Expired invitations marked: {} records updated", updated);
        }
        return updated;
    }
}
