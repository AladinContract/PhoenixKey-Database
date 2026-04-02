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
import com.magiclamp.phoenixkey_db.dto.request.GuardianAddRequest;
import com.magiclamp.phoenixkey_db.dto.request.GuardianRemoveRequest;
import com.magiclamp.phoenixkey_db.dto.response.GuardianAddResponse;
import com.magiclamp.phoenixkey_db.dto.response.GuardianRemoveResponse;
import com.magiclamp.phoenixkey_db.service.GuardianService;

import lombok.RequiredArgsConstructor;

/**
 * Controller quản lý guardians — Social Recovery.
 *
 * Guardian là mạng lưới bảo hộ khôi phục danh tính.
 * Mỗi user nên có 3–5 guardian. Ít hơn → không đủ an toàn.
 * Nhiều hơn → rủi ro guardian malicious cao.
 *
 * Recovery approval (guardian ký lên Smart Contract) được xử lý trực tiếp trên
 * Cardano — PK_DB chỉ quản lý metadata guardians.
 */
@RestController
@RequestMapping("/guardians")
@RequiredArgsConstructor
@Tag(name = "Guardians", description = "Social Recovery — thêm/xóa guardian cho user")
public class GuardianController {

    private final GuardianService guardianService;

    /**
     * Thêm một guardian cho user.
     *
     * **Ai gọi:** NestJS (sau khi user chọn guardian)
     *
     * **ZERO-TRUST:** NestJS verify `proofSignature` (chữ ký từ Root Key/User)
     * trước khi gọi endpoint này.
     *
     * Recovery approval (guardian ký lên Cardano Smart Contract) được xử lý
     * trực tiếp trên Blockchain — không thông qua PK_DB.
     *
     * @param request userDid + guardianDid + proofSignature
     * @return GuardianAddResponse { guardianCount }
     */
    @Operation(summary = "Thêm guardian cho user", description = """
            Thêm một guardian (người bảo hộ) vào mạng lưới khôi phục của user.

            **ZERO-TRUST:**
            - `proofSignature` phải được verify bởi NestJS trước khi gọi
            - Signature chứng minh user thực sự mời người này làm guardian

            **Recovery flow (on-chain, không qua PK_DB):**
            1. User mất thiết bị → cài app mới
            2. User xác thực bằng OTP/Email → khôi phục Seed từ LampNet
            3. User tạo khóa mới, gửi TAAD Tx lên Cardano
            4. Guardians approve Tx trên Cardano
            5. Indexer sync TAAD state → PK_DB

            **Ngưỡng khuyến nghị:** 3–5 guardians.
            """)

    @ApiResponse(responseCode = "201", description = "Guardian đã thêm thành công", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "404", description = "User không tồn tại", content = @Content)
    @ApiResponse(responseCode = "409", description = "Guardian đã tồn tại cho user này", content = @Content)

    @PostMapping("/add")
    public ResponseEntity<DataResponse<GuardianAddResponse>> addGuardian(
            @Valid @RequestBody GuardianAddRequest request) {
        GuardianAddResponse result = guardianService.addGuardian(request);
        return ResponseEntity.ok(
                DataResponse.<GuardianAddResponse>builder()
                        .code(1000)
                        .message("Guardian added")
                        .result(result)
                        .build());
    }

    /**
     * Xóa một guardian của user.
     *
     * **Ai gọi:** NestJS / App (khi user muốn thay đổi guardian)
     *
     * **Soft revoke:** Guardian không bị xóa khỏi DB, chỉ đổi status = "revoked".
     *
     * **Cảnh báo:** Nếu số guardian còn lại < 3, App nên warn user:
     * tài khoản có nguy cơ mất nếu không đủ guardian để khôi phục.
     *
     * @param request userDid + guardianDid
     * @return GuardianRemoveResponse { guardianCount }
     */
    @Operation(summary = "Xóa guardian của user", description = """
            Xóa một guardian khỏi mạng lưới khôi phục của user (soft revoke).

            **Soft revoke:**
            - Guardian không bị xóa khỏi DB — chỉ đổi status = "revoked"
            - User có thể thêm guardian khác thay thế

            **Cảnh báo cho App:**
            - Kiểm tra `guardianCount` trả về
            - Nếu < 3 → warn user: "Cần ít nhất 3 guardians để khôi phục tài khoản"
            - User nên thêm guardian mới ngay

            **Lưu ý:** Xóa guardian KHÔNG trigger recovery.
            Recovery chỉ xảy ra khi đủ guardians approve trên Blockchain.
            """)

    @ApiResponse(responseCode = "200", description = "Guardian đã xóa thành công", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "404", description = "User hoặc guardian không tồn tại", content = @Content)
    @ApiResponse(responseCode = "409", description = "Guardian đã bị revoke trước đó", content = @Content)

    @PostMapping("/remove")
    public ResponseEntity<DataResponse<GuardianRemoveResponse>> removeGuardian(
            @Valid @RequestBody GuardianRemoveRequest request) {
        GuardianRemoveResponse result = guardianService.removeGuardian(request);
        return ResponseEntity.ok(
                DataResponse.<GuardianRemoveResponse>builder()
                        .code(1000)
                        .message("Guardian removed")
                        .result(result)
                        .build());
    }
}
