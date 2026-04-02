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
import com.magiclamp.phoenixkey_db.dto.request.OtpSendRequest;
import com.magiclamp.phoenixkey_db.dto.request.OtpVerifyRequest;
import com.magiclamp.phoenixkey_db.dto.response.OtpVerifyResponse;
import com.magiclamp.phoenixkey_db.service.AuthService;

import lombok.RequiredArgsConstructor;

/**
 * Controller xử lý OTP — save và verify.
 *
 * Security flow:
 * 1. App → NestJS: gửi credential
 * 2. NestJS hash(credential) → blindHash, generate OTP, gửi SMS/Email
 * 3. NestJS → PK_DB: save OTP (blindHash + otp + credential)
 * 4. App nhận OTP, gửi blindHash + otp + ipHash → PK_DB verify
 * 5. PK_DB → App: userDid (nếu đã đăng ký) + blindHash
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "OTP save & verify — Zero-PII flow")
public class AuthController {

    private final AuthService authService;

    /**
     * Lưu OTP vào Redis.
     *
     * **Ai gọi:** NestJS
     *
     * Flow:
     * - NestJS nhận credential từ App
     * - NestJS hash(credential, pepper) → blindHash
     * - NestJS generate OTP → gửi SMS/Email cho user
     * - NestJS gọi endpoint này để lưu OTP vào Redis
     *
     * **Zero-PII:** PK_DB nhận blindHash (không biết credential thật).
     * `credential` chỉ dùng để re-hash khi pepper rotate — không lưu vào DB.
     * `ipHash` dùng để log và rate limit theo IP.
     *
     * @param request blindHash + otp + provider + credential + ipHash
     * @return 200 OK
     */
    @Operation(summary = "Lưu OTP vào Redis", description = """
            NestJS gọi sau khi generate OTP.
            Lưu OTP + credential + ipHash vào Redis (TTL 5 phút).

            **Zero-PII:** `blindHash` = HMAC(credential, pepper). PK_DB không biết credential thật.
            `credential` chỉ dùng in-memory để re-hash khi pepper rotate.
            `ipHash` = SHA-256(IP) — không lưu IP thật.
            """)
    @ApiResponse(responseCode = "200", description = "OTP đã lưu thành công", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    @PostMapping("/otp/save")
    public ResponseEntity<DataResponse<Void>> saveOtp(@Valid @RequestBody OtpSendRequest request) {
        authService.saveOtp(request);
        return ResponseEntity.ok(
                DataResponse.<Void>builder()
                        .code(1000)
                        .message("OTP saved")
                        .build());
    }

    /**
     * Verify OTP trong Redis.
     *
     * **Ai gọi:** App
     *
     * Flow:
     * - App nhận OTP qua SMS/Email
     * - App gửi blindHash + otp + ipHash → PK_DB
     * - PK_DB lookup Redis → so sánh OTP
     * - Đúng → re-hash blind_index_hash nếu pepper đã rotate → trả userDid
     *
     * **Trả về:**
     * - `userDid != null`: user đã đăng ký → đăng nhập thành công
     * - `userDid == null`: user mới → chưa đăng ký (App sẽ gọi /identity/register)
     *
     * @param request blindHash + otp + ipHash
     * @return OtpVerifyResponse { userDid, blindHash }
     */
    @Operation(summary = "Verify OTP", description = """
            App gọi sau khi nhận OTP qua SMS/Email.

            **Logic:**
            - Lookup OTP trong Redis bằng blindHash
            - So sánh OTP → đúng: xóa OTP, tìm user, re-hash nếu pepper đã rotate
            - Sai: throw OTP_INVALID

            **Trả về:**
            - `userDid != null`: user đã đăng ký → đăng nhập thành công
            - `userDid == null`: user mới → chưa đăng ký

            **Re-hash:** Nếu pepper_version trong DB < current pepper_version,
            PK_DB tự động re-hash blind_index_hash với pepper mới.
            """)
    @ApiResponse(responseCode = "200", description = "OTP đúng — đăng nhập thành công hoặc user mới", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "400", description = "OTP sai hoặc hết hạn", content = @Content)
    @ApiResponse(responseCode = "429", description = "OTP đã nhập sai 5 lần — khóa 5 phút", content = @Content)
    @PostMapping("/otp/verify")
    public ResponseEntity<DataResponse<OtpVerifyResponse>> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        OtpVerifyResponse result = authService.verifyOtp(request);
        return ResponseEntity.ok(
                DataResponse.<OtpVerifyResponse>builder()
                        .code(1000)
                        .message("OTP verified")
                        .result(result)
                        .build());
    }
}
