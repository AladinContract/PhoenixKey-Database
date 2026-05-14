package com.magiclamp.phoenixkey_db.controller;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.dto.recovery.RecoveryDtos.*;
import com.magiclamp.phoenixkey_db.security.AuthenticatedUser;
import com.magiclamp.phoenixkey_db.service.recovery.RecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/recovery")
@RequiredArgsConstructor
@Tag(name = "Recovery", description = "Guardian-initiated recovery (timelock 7d mainnet / 1h preprod)")
public class RecoveryController {

    private final RecoveryService recoveryService;

    @Operation(summary = "Init recovery — guardians sign new pubkey + deposit collateral")
    @PostMapping("/init")
    public ResponseEntity<DataResponse<InitRecoveryResponse>> init(
            @Valid @RequestBody InitRecoveryRequest request) {
        return ResponseEntity.ok(DataResponse.<InitRecoveryResponse>builder()
                .code(1000).result(recoveryService.init(request)).build());
    }

    @Operation(summary = "Cancel recovery (owner only, before deadline)")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<DataResponse<Void>> cancel(
            AuthenticatedUser auth,
            @PathVariable("id") UUID recoveryId,
            @Valid @RequestBody CancelRecoveryRequest request) {
        recoveryService.cancel(recoveryId, auth.userDid(), request);
        return ResponseEntity.ok(DataResponse.<Void>builder()
                .code(1000).message("Recovery cancelled").build());
    }

    @Operation(summary = "Finalize recovery (anyone, after deadline)")
    @PostMapping("/{id}/finalize")
    public ResponseEntity<DataResponse<FinalizeRecoveryResponse>> finalizeRecovery(
            @PathVariable("id") UUID recoveryId) {
        return ResponseEntity.ok(DataResponse.<FinalizeRecoveryResponse>builder()
                .code(1000).result(recoveryService.finalize(recoveryId)).build());
    }

    @Operation(summary = "Get recovery status for a user")
    @GetMapping("/{userDid}/status")
    public ResponseEntity<DataResponse<RecoveryStatusResponse>> status(
            @PathVariable("userDid") String userDid) {
        return ResponseEntity.ok(DataResponse.<RecoveryStatusResponse>builder()
                .code(1000).result(recoveryService.getStatus(userDid)).build());
    }
}
