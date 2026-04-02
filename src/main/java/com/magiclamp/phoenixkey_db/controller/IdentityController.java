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
import com.magiclamp.phoenixkey_db.dto.request.UserDidUpdateRequest;
import com.magiclamp.phoenixkey_db.dto.response.IdentityPubkeyResponse;
import com.magiclamp.phoenixkey_db.dto.response.IdentityRegisterResponse;
import com.magiclamp.phoenixkey_db.dto.response.IdentityStatusResponse;
import com.magiclamp.phoenixkey_db.service.IdentityService;

import lombok.RequiredArgsConstructor;

/**
 * Controller xử lý Identity — đăng ký, tra cứu pubkey/status, update DID.
 *
 * SSoT: Blockchain Cardano. PK_DB chỉ là cache/indexer.
 */
@RestController
@RequestMapping("/identity")
@RequiredArgsConstructor
@Tag(name = "Identity", description = "Register, tra cứu pubkey/status, update DID — Blockchain SSoT")
public class IdentityController {

    private final IdentityService identityService;

    /**
     * Đăng ký identity mới.
     *
     * **Ai gọi:** NestJS (sau khi App hoàn tất OTP verify)
     *
     * Flow:
     * 1. App đăng nhập bằng OTP → NestJS verify → App nhận blindHash +
     * userDid="pending"
     * 2. App gửi pubkey + signature → NestJS
     * 3. NestJS mint DID trên Cardano
     * 4. NestJS gọi endpoint này → PK_DB tạo user với userDid="pending"
     * 5. Sau khi DID mint xong → NestJS gọi PUT /identity/did để update DID thực sự
     *
     * **Zero-PII:** PK_DB nhận blindHash (HMAC of credential), không biết
     * credential thật.
     *
     * @param request credential + pubkey + signature + keyRole + provider
     * @return IdentityRegisterResponse { userId, userDid="pending" }
     */
    @Operation(summary = "Đăng ký identity mới", description = """
            NestJS gọi sau khi App hoàn tất OTP verify và gửi pubkey.

            **Tạo:**
            - `users` row: userId (UUIDv7), userDid="pending"
            - `auth_methods` row: blindHash (HMAC of credential, pepper)
            - `authorized_keys` row: owner key

            **Lưu ý:** userDid ban đầu là "pending".
            Sau khi NestJS mint DID trên Cardano → gọi PUT /identity/did để update.

            **Zero-PII:** credential không lưu vào DB — chỉ blindHash.
            """)

    @ApiResponse(responseCode = "201", description = "Identity đã đăng ký, userDid='pending'", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    @ApiResponse(responseCode = "409", description = "Credential đã đăng ký", content = @Content)
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
     * Lấy public key của một user qua DID.
     *
     * **Ai gọi:** OriLife / AladinWork Backend (khi verify chữ ký)
     *
     * App OriLife ký dữ liệu bằng Hardware Key → OriLife Backend gọi endpoint này
     * để lấy Hardware PubKey → OriLife Backend tự verify chữ ký (Decoupled).
     *
     * **PK_DB không verify signature** — chỉ trả pubkey. Signature được verify ở
     * Backend.
     *
     * @param userDid DID của user (VD: did:prism:...)
     * @return IdentityPubkeyResponse { publicKeyHex, keyRole }
     */
    @Operation(summary = "Lấy public key của user qua DID", description = """
            Tra cứu owner public key của một DID.

            **Use case:** OriLife/AladinWork Backend verify chữ ký của App.
            Backend gọi endpoint này → nhận Hardware PubKey → verify offline.

            **Lưu ý:** PK_DB không verify signature. Signature được verify ở Backend.
            """)

    @ApiResponse(responseCode = "200", description = "Public key của user", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "404", description = "DID không tồn tại hoặc không có owner key", content = @Content)

    @GetMapping("/{did}/pubkey")
    public ResponseEntity<DataResponse<IdentityPubkeyResponse>> getPubkey(
            @Parameter(description = "DID của user (VD: did:prism:4d8e...abc)", example = "did:prism:4d8e123456789abcdef") @PathVariable("did") String userDid) {
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
     * **Ai gọi:** App (hiển thị trạng thái ví)
     *
     * Trả về trạng thái từ bảng `onchain_taad_state_cache`.
     * Bảng này chỉ được ghi bởi Indexer Worker — sync từ Blockchain Cardano.
     *
     * @param userDid DID của user
     * @return IdentityStatusResponse { status, controllerPkh, sequence,
     *         recoveryDeadline }
     */
    @Operation(summary = "Lấy trạng thái TAAD của user", description = """
            Tra cứu trạng thái TAAD (Transparent Account Damage & Recovery).

            **Nguồn dữ liệu:** Bảng `onchain_taad_state_cache` — chỉ ghi bởi Indexer Worker.
            Indexer Worker sync từ Blockchain Cardano sau mỗi block.

            **Trạng thái:**
            - `ACTIVE`: tài khoản hoạt động bình thường
            - `RECOVERING`: đang trong quá trình khôi phục (deadline countdown)
            - `MIGRATED`: đã di chuyển sang controller mới
            """)

    @ApiResponse(responseCode = "200", description = "Trạng thái TAAD của user", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "404", description = "DID không tồn tại hoặc chưa có TAAD state", content = @Content)
    @GetMapping("/{did}/status")
    public ResponseEntity<DataResponse<IdentityStatusResponse>> getStatus(
            @Parameter(description = "DID của user", example = "did:prism:4d8e123456789abcdef") @PathVariable("did") String userDid) {
        IdentityStatusResponse result = identityService.getStatus(userDid);
        return ResponseEntity.ok(
                DataResponse.<IdentityStatusResponse>builder()
                        .code(1000)
                        .message("Status retrieved")
                        .result(result)
                        .build());
    }

    /**
     * Update userDid sau khi NestJS mint DID trên Cardano.
     *
     * **Ai gọi:** NestJS
     *
     * Flow:
     * 1. App đăng ký → PK_DB tạo user với userDid="pending"
     * 2. NestJS mint DID trên Cardano
     * 3. NestJS gọi endpoint này → update userDid thực sự
     *
     * @param request userId (UUID từ bước 1) + userDid (DID đã mint)
     * @return 200 OK
     */
    @Operation(summary = "Update userDid sau khi mint DID trên Cardano", description = """
            NestJS gọi sau khi mint DID thành công trên Cardano.

            **Logic:**
            - Tìm user theo userId
            - Kiểm tra DID mới chưa được gán cho user khác
            - Update userDid từ "pending" → DID thực sự

            **Sau khi gọi:** User hoàn toàn hoạt động với DID thực sự.
            """)

    @ApiResponse(responseCode = "200", description = "DID đã update thành công", content = @Content)
    @ApiResponse(responseCode = "404", description = "User không tồn tại", content = @Content)
    @ApiResponse(responseCode = "409", description = "DID đã được gán cho user khác", content = @Content)
    @PutMapping("/did")
    public ResponseEntity<DataResponse<Void>> updateDid(@Valid @RequestBody UserDidUpdateRequest request) {
        identityService.updateUserDid(request);
        return ResponseEntity.ok(
                DataResponse.<Void>builder()
                        .code(1000)
                        .message("DID updated")
                        .build());
    }
}
