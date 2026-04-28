package com.magiclamp.phoenixkey_db.controller;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.dto.sign.SeedExportRequest;
import com.magiclamp.phoenixkey_db.dto.sign.SignIntent;
import com.magiclamp.phoenixkey_db.dto.sign.SignRequestCreateRequest;
import com.magiclamp.phoenixkey_db.dto.sign.SignRequestCreateResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.service.sign.SignRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Seed Phrase export flow (spec §9.2).
 *
 * <p>Endpoint thin wrapper trên {@link SignRequestService} — build sẵn intent
 * type {@code SEED_EXPORT}. Khi mobile approve, side-effect ghi
 * {@code users.seed_exported_at = NOW()}.</p>
 */
@RestController
@RequestMapping("/seed")
@RequiredArgsConstructor
@Tag(name = "Seed", description = "Seed Phrase export flow (spec §9.2)")
public class SeedController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DOMAIN = "phoenixkey.me";
    private static final String APP_ID = "phoenixkey-web-v1";

    private final SignRequestService signRequestService;

    @Operation(summary = "Trigger Seed Phrase export", description = """
            Tạo SignRequest đặc biệt với intent.type = SEED_EXPORT. Mobile sẽ
            hiển thị cảnh báo bảo mật + yêu cầu biometric trước khi show 24 từ.

            Khi mobile approve:
            - Server set users.seed_exported_at = NOW() (spec §9.5)
            - activity_logs ghi action seed_phrase_exported
            - Dashboard banner chuyển sang trạng thái cảnh báo (vàng < 72h, đỏ ≥ 72h)
              cho tới khi user thực hiện Key Rotation.

            **Bearer:** session_token (24h TTL).
            """)
    @ApiResponse(responseCode = "200", description = "Sign request created", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "403", description = "Invalid session token")
    @PostMapping("/export-request")
    public ResponseEntity<DataResponse<SignRequestCreateResponse>> exportRequest(
            @Valid @RequestBody SeedExportRequest request,
            @RequestHeader("Authorization") String authorization) {

        String sessionToken = parseBearer(authorization);

        SignIntent intent = new SignIntent(
                "SEED_EXPORT",
                null,
                DOMAIN,
                APP_ID,
                randomNonce(),
                Instant.now().getEpochSecond(),
                request.displayTextOrDefault());

        SignRequestCreateRequest signReq = new SignRequestCreateRequest(request.sessionId(), intent);
        SignRequestCreateResponse result = signRequestService.create(signReq, sessionToken);

        return ResponseEntity.ok(
                DataResponse.<SignRequestCreateResponse>builder()
                        .code(1000)
                        .message("Seed export request created")
                        .result(result)
                        .build());
    }

    private static String randomNonce() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String parseBearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "missing or invalid Bearer header");
        }
        return authorization.substring(7).trim();
    }
}
