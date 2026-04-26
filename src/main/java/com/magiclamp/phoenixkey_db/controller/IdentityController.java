package com.magiclamp.phoenixkey_db.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.dto.request.IdentityRegisterRequest;
import com.magiclamp.phoenixkey_db.dto.response.IdentityPubkeyResponse;
import com.magiclamp.phoenixkey_db.dto.response.IdentityRegisterResponse;
import com.magiclamp.phoenixkey_db.dto.response.IdentityStatusResponse;
import com.magiclamp.phoenixkey_db.service.IdentityService;

import lombok.RequiredArgsConstructor;

/**
 * Controller xử lý Identity — đăng ký, tra cứu pubkey/status.
 *
 * SSoT: Blockchain Cardano. PostgreSQL chỉ là cache/indexer của TAAD state.
 */
@RestController
@RequestMapping("/identity")
@RequiredArgsConstructor
@Tag(name = "Identity", description = "Register, tra cứu pubkey/status — Cardano là SSoT")
public class IdentityController {

    private final IdentityService identityService;

    /**
     * Đăng ký identity mới — 1-step.
     *
     * Mobile gọi sau khi Hardware Key được sinh trong Secure Enclave/TEE.
     * Server verify Genesis signature → publish DID lên Cardano → insert users +
     * authorized_keys.
     */
    @Operation(summary = "Đăng ký identity mới", description = """
            Mobile gọi sau khi sinh Hardware Key.

            **Flow:**
            1. Verify Genesis signature trên `"PHOENIXKEY_GENESIS:" + publicKeyHex`
            2. Publish DID Document lên Cardano qua CardanoService.createDID()
            3. Insert `users` + `authorized_keys` với userDid = `did:cardano:<network>:<txHash>`
            """)
    @ApiResponse(responseCode = "200", description = "Identity đã đăng ký", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    @ApiResponse(responseCode = "403", description = "Genesis signature invalid", content = @Content)
    @PostMapping("/register")
    public ResponseEntity<DataResponse<IdentityRegisterResponse>> register(
            @Valid @RequestBody IdentityRegisterRequest request) {
        IdentityRegisterResponse result = identityService.register(request);
        return ResponseEntity.ok(
                DataResponse.<IdentityRegisterResponse>builder()
                        .code(1000)
                        .message("Identity registered")
                        .result(result)
                        .build());
    }

    /**
     * Lấy public key của một user qua DID. Dùng cho verify chữ ký từ Backend khác.
     */
    @Operation(summary = "Lấy public key của user qua DID", description = """
            Tra cứu owner public key của một DID.

            **Use case:** OriLife/AladinWork Backend verify chữ ký của App.
            Backend gọi endpoint này → nhận Hardware PubKey → verify offline.
            """)
    @ApiResponse(responseCode = "200", description = "Public key của user", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "404", description = "DID không tồn tại hoặc không có owner key", content = @Content)
    @GetMapping("/{did}/pubkey")
    public ResponseEntity<DataResponse<IdentityPubkeyResponse>> getPubkey(
            @Parameter(description = "DID của user", example = "did:cardano:preprod:a3f9b2e8...") @PathVariable("did") String userDid) {
        IdentityPubkeyResponse result = identityService.getPubkey(userDid);
        return ResponseEntity.ok(
                DataResponse.<IdentityPubkeyResponse>builder()
                        .code(1000)
                        .message("Public key retrieved")
                        .result(result)
                        .build());
    }

    /**
     * Lấy trạng thái TAAD của một user qua DID.
     *
     * Nguồn dữ liệu: bảng {@code onchain_taad_state_cache}, ghi bởi Indexer Worker.
     */
    @Operation(summary = "Lấy trạng thái TAAD của user", description = """
            Tra cứu trạng thái TAAD (Transparent Account Damage & Recovery).

            **Nguồn dữ liệu:** Bảng `onchain_taad_state_cache` — ghi bởi Indexer Worker
            sync từ Blockchain Cardano sau mỗi block.

            **Trạng thái:**
            - `ACTIVE`: tài khoản hoạt động bình thường
            - `RECOVERING`: đang trong quá trình khôi phục
            - `MIGRATED`: đã di chuyển sang controller mới
            """)
    @ApiResponse(responseCode = "200", description = "Trạng thái TAAD của user", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "404", description = "DID không tồn tại hoặc chưa có TAAD state", content = @Content)
    @GetMapping("/{did}/status")
    public ResponseEntity<DataResponse<IdentityStatusResponse>> getStatus(
            @Parameter(description = "DID của user", example = "did:cardano:preprod:a3f9b2e8...") @PathVariable("did") String userDid) {
        IdentityStatusResponse result = identityService.getStatus(userDid);
        return ResponseEntity.ok(
                DataResponse.<IdentityStatusResponse>builder()
                        .code(1000)
                        .message("Status retrieved")
                        .result(result)
                        .build());
    }
}
