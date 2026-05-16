package com.magiclamp.phoenixkey_db.dto.recovery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public final class RecoveryDtos {

    private RecoveryDtos() {}

    public record GuardianSignature(
            @NotBlank String guardianDid,
            @NotBlank String publicKeyHex,
            @NotBlank String signature
    ) {}

    public record InitRecoveryRequest(
            @NotBlank String userDid,
            @NotBlank String newPublicKeyHex,
            @NotBlank String nonce,
            @NotBlank String collateralAddress,
            @NotEmpty @Valid List<GuardianSignature> guardianSignatures
    ) {}

    public record InitRecoveryResponse(
            String recoveryId,
            String cardanoTxHash,
            long deadlineSlot,
            String status
    ) {}

    public record CancelRecoveryRequest(
            @NotBlank String nonce,
            @NotBlank String ownerPublicKeyHex,
            @NotBlank String ownerSignature
    ) {}

    public record FinalizeRecoveryResponse(
            String recoveryId,
            String cardanoTxHash,
            String status
    ) {}

    public record RecoveryStatusResponse(
            String recoveryId,
            String status,
            long remainingSlots,
            String pendingPubkeyHex,
            List<GuardianSignature> guardianVotes
    ) {}
}
