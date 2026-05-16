package com.magiclamp.phoenixkey_db.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class WalletDtos {

    private WalletDtos() {}

    public record WalletRegisterRequest(
            @NotBlank
            @Pattern(regexp = "^addr_(test1|1)[a-z0-9]+$")
            @Schema(example = "addr_test1qz...")
            String walletAddress
    ) {}

    public record BalanceResponse(
            String address,
            long balanceLovelace,
            long balanceLamp,
            long balanceMagic,
            long magicAccrued,
            String magicRatePerSlot,    // String to avoid float precision issues
            long lastAccrualSlot,
            long currentSlot
    ) {}

    public record MagicClaimResponse(
            String claimId,
            long amountMagic,
            String cardanoTxHash,
            String status
    ) {}
}
