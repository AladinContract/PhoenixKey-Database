package com.magiclamp.phoenixkey_db.controller;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.common.UuidGenerator;
import com.magiclamp.phoenixkey_db.domain.DeviceToken;
import com.magiclamp.phoenixkey_db.dto.device.DeviceRegisterRequest;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.DeviceTokenRepository;
import com.magiclamp.phoenixkey_db.security.JwtService;
import com.magiclamp.phoenixkey_db.security.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Mobile push token registration (Phase D.4).
 *
 * <p>Mobile gọi sau khi login (có session_token) để lưu FCM/APNs token. Server
 * lưu vào {@code device_tokens} → dùng khi cần gửi push.</p>
 */
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Devices", description = "Mobile push token registration (Phase D.4)")
public class DeviceController {

    private final JwtService jwtService;
    private final DeviceTokenRepository deviceTokenRepository;
    private final UuidGenerator uuidGenerator;

    @Operation(summary = "Register push token", description = """
            Mobile gọi sau khi login để server lưu FCM/APNs token. Khi web/server
            tạo sign-request hoặc session-approval, server lookup theo userDid +
            gửi push tới mọi device đã đăng ký.

            **Bearer:** session_token (24h TTL).
            **Idempotent:** gọi lại với cùng token → tạo row mới (mobile có thể có
            cả APNs + FCM). Cleanup row cũ khi token revoke (báo lỗi từ FCM).
            """)
    @ApiResponse(responseCode = "200", description = "Device registered", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "400", description = "Cần ít nhất 1 trong fcmToken/apnsToken")
    @ApiResponse(responseCode = "403", description = "Invalid session token")
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<DataResponse<Void>> register(
            @Valid @RequestBody DeviceRegisterRequest request,
            @RequestHeader("Authorization") String authorization) {

        if (!request.hasAnyToken()) {
            throw new AppException(ErrorCode.SYSTEM_INTERNAL_ERROR,
                    "Cần ít nhất 1 trong fcmToken/apnsToken");
        }

        String sessionToken = parseBearer(authorization);
        Claims claims = jwtService.parseAndVerify(sessionToken);
        if (!JwtServiceImpl.TYPE_SESSION.equals(claims.get("type", String.class))) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "not a session token");
        }
        String userDid = claims.getSubject();

        DeviceToken device = DeviceToken.builder()
                .id(uuidGenerator.create())
                .userDid(userDid)
                .platform(request.platform())
                .fcmToken(request.fcmToken())
                .apnsToken(request.apnsToken())
                .createdAt(OffsetDateTime.now())
                .build();
        deviceTokenRepository.save(device);

        log.info("Device registered: userDid={}, platform={}, hasFcm={}, hasApns={}",
                userDid, request.platform(),
                request.fcmToken() != null, request.apnsToken() != null);

        return ResponseEntity.ok(
                DataResponse.<Void>builder()
                        .code(1000)
                        .message("Device registered")
                        .build());
    }

    private static String parseBearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "missing or invalid Bearer header");
        }
        return authorization.substring(7).trim();
    }
}
