package com.magiclamp.phoenixkey_db.controller;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.dto.wallet.WalletDtos.*;
import com.magiclamp.phoenixkey_db.security.AuthenticatedUser;
import com.magiclamp.phoenixkey_db.service.wallet.BalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Wallet address registration, balance, MAGIC claim")
public class WalletController {

    private final BalanceService balanceService;

    @Operation(summary = "Register wallet address (mobile gọi sau khi derive)")
    @PostMapping("/register")
    public ResponseEntity<DataResponse<Void>> registerAddress(
            AuthenticatedUser auth,
            @Valid @RequestBody WalletRegisterRequest request) {
        balanceService.registerWalletAddress(auth.userDid(), request.walletAddress());
        return ResponseEntity.ok(DataResponse.<Void>builder()
                .code(1000).message("Wallet address registered").build());
    }

    @Operation(summary = "Get balance + accrued MAGIC")
    @GetMapping("/{userDid}/balance")
    public ResponseEntity<DataResponse<BalanceResponse>> balance(@PathVariable("userDid") String userDid) {
        // Public endpoint — anyone can query balance by DID
        // (Cardano addresses are public anyway)
        return ResponseEntity.ok(DataResponse.<BalanceResponse>builder()
                .code(1000).result(balanceService.getBalance(userDid)).build());
    }

    @Operation(summary = "Claim accrued MAGIC — mint + send to wallet")
    @PostMapping("/magic/claim")
    public ResponseEntity<DataResponse<MagicClaimResponse>> claimMagic(AuthenticatedUser auth) {
        return ResponseEntity.ok(DataResponse.<MagicClaimResponse>builder()
                .code(1000).result(balanceService.claimMagic(auth.userDid())).build());
    }
}
