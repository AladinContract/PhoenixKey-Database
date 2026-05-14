package com.magiclamp.phoenixkey_db.controller;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.dto.activation.ActivationDtos.*;
import com.magiclamp.phoenixkey_db.security.AuthenticatedUser;
import com.magiclamp.phoenixkey_db.service.activation.ActivationService;
import com.magiclamp.phoenixkey_db.service.sse.ActivationEventBus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/activation")
@RequiredArgsConstructor
@Tag(name = "Activation", description = "Activation package flow — mua 200k VND → 1001 LAMP + 10 ADA")
public class ActivationController {

    private final ActivationService activationService;
    private final ActivationEventBus eventBus;

    @Value("${phoenixkey.activation.admin-token:}")
    private String adminToken;

    /**
     * User initiate gói kích hoạt. Yêu cầu session_token.
     */
    @Operation(summary = "Initiate activation package")
    @PostMapping("/initiate")
    public ResponseEntity<DataResponse<ActivationInitiateResponse>> initiate(
            AuthenticatedUser auth,
            @Valid @RequestBody ActivationInitiateRequest request) {
        ActivationInitiateResponse result = activationService.initiate(auth.userDid(), request.walletAddress());
        return ResponseEntity.ok(DataResponse.<ActivationInitiateResponse>builder()
                .code(1000).message("Activation initiated").result(result).build());
    }

    /**
     * Payment gateway webhook hoặc admin manual. Gated bằng X-Admin-Token header
     * cho testnet — production sẽ verify HMAC signature từ payment provider.
     */
    @Operation(summary = "Confirm payment (webhook hoặc admin)")
    @PostMapping("/{id}/confirm-payment")
    public ResponseEntity<DataResponse<Void>> confirmPayment(
            @PathVariable("id") UUID activationId,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @Valid @RequestBody ActivationConfirmPaymentRequest request) {
        verifyAdminToken(token);
        activationService.confirmPayment(activationId, request.paymentReference());
        return ResponseEntity.ok(DataResponse.<Void>builder()
                .code(1000).message("Payment confirmed").build());
    }

    /**
     * Genie's mobile gọi sau khi ký tx Cardano. Yêu cầu session_token Genie.
     */
    @Operation(summary = "Submit signed Cardano tx (Genie only)")
    @PostMapping("/{id}/submit-tx")
    public ResponseEntity<DataResponse<ActivationSubmitTxResponse>> submitTx(
            AuthenticatedUser auth,
            @PathVariable("id") UUID activationId,
            @Valid @RequestBody ActivationSubmitTxRequest request) {
        ActivationSubmitTxResponse result = activationService.submitTx(
                activationId, auth.userDid(), request.signedTxCbor());
        return ResponseEntity.ok(DataResponse.<ActivationSubmitTxResponse>builder()
                .code(1000).message("Tx submitted").result(result).build());
    }

    @Operation(summary = "Cancel activation (user only, chỉ khi PENDING_PAYMENT)")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<DataResponse<Void>> cancel(
            AuthenticatedUser auth,
            @PathVariable("id") UUID activationId) {
        activationService.cancel(activationId, auth.userDid());
        return ResponseEntity.ok(DataResponse.<Void>builder()
                .code(1000).message("Activation cancelled").build());
    }

    @Operation(summary = "Get status (polling fallback)")
    @GetMapping("/{id}/status")
    public ResponseEntity<DataResponse<ActivationStatusResponse>> status(
            @PathVariable("id") UUID activationId) {
        return ResponseEntity.ok(DataResponse.<ActivationStatusResponse>builder()
                .code(1000).result(activationService.getStatus(activationId)).build());
    }

    @Operation(summary = "SSE stream cho activation lifecycle events")
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @Parameter(description = "activation_id") @PathVariable("id") UUID activationId,
            @RequestHeader("Authorization") String authorization) {
        // Bearer parsing handled in interceptor (auth filter) — here we just register
        return eventBus.register(activationId.toString());
    }

    // ────────────────────────────────────────────────────────────

    private void verifyAdminToken(String token) {
        if (adminToken == null || adminToken.isBlank()) {
            throw new SecurityException("Admin token not configured");
        }
        if (!adminToken.equals(token)) {
            throw new SecurityException("Invalid admin token");
        }
    }
}
