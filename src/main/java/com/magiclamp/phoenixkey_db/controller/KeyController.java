package com.magiclamp.phoenixkey_db.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.dto.request.KeyAuthorizeRequest;
import com.magiclamp.phoenixkey_db.dto.request.KeyRevokeRequest;
import com.magiclamp.phoenixkey_db.dto.response.KeyAuthorizeResponse;
import com.magiclamp.phoenixkey_db.service.KeyService;

import lombok.RequiredArgsConstructor;

/**
 * Controller quản lý authorized keys — authorize và revoke.
 *
 * Một user có thể có nhiều thiết bị/khóa (owner, farm_manager, read_only).
 * ZERO-TRUST: addedBySignature được verify bởi NestJS trước khi gọi.
 */
@RestController
@RequestMapping("/keys")
@RequiredArgsConstructor
@Tag(name = "Keys", description = "Authorize & revoke thiết bị/khóa — Zero-Trust multi-device")
public class KeyController {

    private final KeyService keyService;

    /**
     * Authorize một public key mới cho user.
     *
     * **Ai gọi:** NestJS / App (sau khi user thêm thiết bị mới)
     *
     * **ZERO-TRUST:** NestJS verify `addedBySignature` (chữ ký từ Root Key)
     * trước khi gọi endpoint này.
     * PK_DB chỉ nhận và lưu — không verify được vì không có root public key.
     *
     * **Cho phép re-authorize:** Key đã revoke trước đó có thể authorize lại.
     *
     * @param request userDid + publicKeyHex + keyRole + addedBySignature
     * @return KeyAuthorizeResponse { keyId }
     */
    @Operation(summary = "Authorize public key mới cho user", description = """
            Thêm một public key (thiết bị) vào danh sách authorized keys của user.

            **ZERO-TRUST:**
            - `addedBySignature` phải được verify bởi NestJS trước khi gọi
            - NestJS có root public key để verify chữ ký
            - PK_DB chỉ nhận và lưu — không verify được

            **Re-authorize:**
            - Key đã revoke trước đó có thể authorize lại
            - Key đang active của user hiện tại không thể authorize lại

            **Key Roles:**
            - `owner`: thiết bị gốc, toàn quyền
            - `farm_manager`: ký giao dịch liên quan đến farm
            - `read_only`: chỉ đọc, không ký
            """)

    @ApiResponse(responseCode = "201", description = "Key đã authorized thành công", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "404", description = "User không tồn tại", content = @Content)
    @ApiResponse(responseCode = "409", description = "Key đã authorized cho user này (active)", content = @Content)
    @PostMapping("/authorize")
    public ResponseEntity<DataResponse<KeyAuthorizeResponse>> authorize(
            @Valid @RequestBody KeyAuthorizeRequest request) {
        KeyAuthorizeResponse result = keyService.authorize(request);
        return ResponseEntity.ok(
                DataResponse.<KeyAuthorizeResponse>builder()
                        .code(1000)
                        .message("Key authorized")
                        .result(result)
                        .build());
    }

    /**
     * Revoke một public key của user.
     *
     * **Ai gọi:** NestJS / App (khi user mất thiết bị hoặc muốn xóa thiết bị)
     *
     * **Soft revoke:** Key không bị xóa khỏi DB, chỉ đổi status = "revoked".
     * Key đã revoke có thể được authorize lại sau.
     *
     * @param request userDid + publicKeyHex
     * @return 200 OK
     */
    @Operation(summary = "Revoke public key của user", description = """
            Xóa quyền của một public key (thiết bị).

            **Soft revoke:**
            - Key không bị xóa khỏi DB — chỉ đổi status = "revoked"
            - Key đã revoke có thể được authorize lại sau (re-enrollment)
            - Thiết bị cũ không thể ký giao dịch sau khi revoke

            **Security:** Sau khi revoke, thiết bị bị revoke không thể:
            - Ký giao dịch trên Cardano
            - Truy cập vào hệ thống OriLife/AladinWork
            """)

    @ApiResponse(responseCode = "200", description = "Key đã revoke thành công", content = @Content)
    @ApiResponse(responseCode = "404", description = "User hoặc key không tồn tại", content = @Content)
    @ApiResponse(responseCode = "409", description = "Key đã bị revoke trước đó", content = @Content)
    @PostMapping("/revoke")
    public ResponseEntity<DataResponse<Void>> revoke(@Valid @RequestBody KeyRevokeRequest request) {
        keyService.revoke(request);
        return ResponseEntity.ok(
                DataResponse.<Void>builder()
                        .code(1000)
                        .message("Key revoked")
                        .build());
    }
}
