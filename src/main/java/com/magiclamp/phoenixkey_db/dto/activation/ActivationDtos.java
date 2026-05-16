package com.magiclamp.phoenixkey_db.dto.activation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// All DTOs for the activation flow live in this single file for easy review.
// Split into individual files once merged into the main repo (one per record).

public final class ActivationDtos {

    private ActivationDtos() {}

    // ─── Request ──────────────────────────────────────────────────

    public record ActivationInitiateRequest(
            @NotBlank
            @Pattern(regexp = "^addr_(test1|1)[a-z0-9]+$",
                    message = "wallet_address phải là Bech32 Shelley address")
            @Schema(example = "addr_test1qz...")
            String walletAddress
    ) {}

    public record ActivationConfirmPaymentRequest(
            // Nullable on purpose: admin-token path (testnet mock) omits this;
            // production webhook gateway sends the real reference. Service layer
            // still rejects non-null mismatch — see ActivationServiceImpl.
            @Schema(example = "PK12345678ABC123",
                    description = "Payment reference — required for gateway webhook, optional for admin-token mock")
            String paymentReference
    ) {}

    public record ActivationSubmitTxRequest(
            @NotBlank
            @Schema(description = "Hex-encoded signed Cardano transaction CBOR")
            String signedTxCbor
    ) {}

    // ─── Response ─────────────────────────────────────────────────

    public record ActivationInitiateResponse(
            String activationId,
            String paymentQrUrl,
            int amountVnd,
            long amountLamp,
            long amountLovelace,
            String genieDid,
            String proofChatUrl,
            long expiresAt
    ) {}

    public record ActivationSubmitTxResponse(
            String cardanoTxHash,
            String status
    ) {}

    public record ActivationStatusResponse(
            String activationId,
            String status,
            String cardanoTxHash,
            long expiresAt,
            String failReason
    ) {}
}
