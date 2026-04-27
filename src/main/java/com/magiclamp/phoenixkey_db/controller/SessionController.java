package com.magiclamp.phoenixkey_db.controller;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.dto.session.SessionApproveRequest;
import com.magiclamp.phoenixkey_db.dto.session.SessionApproveResponse;
import com.magiclamp.phoenixkey_db.dto.session.SessionInitResponse;
import com.magiclamp.phoenixkey_db.dto.session.SessionPushRequest;
import com.magiclamp.phoenixkey_db.dto.session.SessionStatusResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.service.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * QR-pairing session endpoints (spec §6).
 *
 * <p>Web flow:
 * <ol>
 *   <li>{@code POST /init} → {sessionId, challenge, tempToken, expiresAt}</li>
 *   <li>{@code GET /{id}/stream} → SSE stream với Bearer tempToken</li>
 *   <li>(parallel) Hiển thị QR cho user quét</li>
 *   <li>SSE nhận event {@code "approved"} với {sessionToken, linkedDeviceToken, userDid}</li>
 *   <li>(reconnect fallback) {@code GET /{id}/status} với Bearer tempToken</li>
 * </ol>
 *
 * <p>Mobile flow:
 * <ol>
 *   <li>Quét QR → decode {sessionId, challenge, domain}</li>
 *   <li>FaceID/biometric → ký challenge bằng Hardware Key</li>
 *   <li>{@code POST /{id}/approve} với {userDid, publicKeyHex, signature, domain, timestamp}</li>
 * </ol>
 */
@RestController
@RequestMapping("/auth/session")
@RequiredArgsConstructor
@Tag(name = "Session", description = "QR-pairing web ↔ mobile session (spec §6)")
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "Init session — web tạo QR challenge")
    @ApiResponse(responseCode = "200", description = "Session created", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @PostMapping("/init")
    public ResponseEntity<DataResponse<SessionInitResponse>> init() {
        SessionInitResponse result = sessionService.init();
        return ResponseEntity.ok(
                DataResponse.<SessionInitResponse>builder()
                        .code(1000)
                        .message("Session created")
                        .result(result)
                        .build());
    }

    @Operation(summary = "Open SSE stream — web subscribe approved event", description = """
            Bearer header phải chứa temp_token từ /init response. Server bind
            tempToken với sessionId — chống ai khác listen session của user.

            Stream emit event "approved" khi mobile phê duyệt, kèm:
              { sessionToken, linkedDeviceToken, userDid }

            Heartbeat: SSE comment ":ping" mỗi 30s để giữ proxy enterprise không
            kill kết nối (spec §15.1).
            """)
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Parameter(description = "session_id") @PathVariable("id") String sessionId,
            @RequestHeader("Authorization") String authorization) {
        return sessionService.openStream(sessionId, parseBearer(authorization));
    }

    @Operation(summary = "Get session status — fallback sau khi SSE reconnect", description = """
            Web gọi sau khi SSE reconnect (network blip) để lấy state hiện tại.
            Nếu mobile đã approve trong lúc disconnect, response trả ngay
            sessionToken — web không phải đợi event tiếp theo.
            """)
    @GetMapping("/{id}/status")
    public ResponseEntity<DataResponse<SessionStatusResponse>> status(
            @PathVariable("id") String sessionId,
            @RequestHeader("Authorization") String authorization) {
        SessionStatusResponse result = sessionService.getStatus(sessionId, parseBearer(authorization));
        return ResponseEntity.ok(
                DataResponse.<SessionStatusResponse>builder()
                        .code(1000)
                        .message("Status retrieved")
                        .result(result)
                        .build());
    }

    @Operation(summary = "Approve session — mobile gọi sau khi ký challenge")
    @ApiResponse(responseCode = "200", description = "Session approved + linked device token")
    @ApiResponse(responseCode = "403", description = "Signature invalid hoặc pubkey không thuộc userDid")
    @ApiResponse(responseCode = "404", description = "Session không tồn tại / hết hạn")
    @ApiResponse(responseCode = "409", description = "Session đã được approve trước đó")
    @PostMapping("/{id}/approve")
    public ResponseEntity<DataResponse<SessionApproveResponse>> approve(
            @PathVariable("id") String sessionId,
            @Valid @RequestBody SessionApproveRequest request) {
        SessionApproveResponse result = sessionService.approveByMobile(sessionId, request);
        return ResponseEntity.ok(
                DataResponse.<SessionApproveResponse>builder()
                        .code(1000)
                        .message("Session approved")
                        .result(result)
                        .build());
    }

    @Operation(summary = "Push to linked device — web trigger push thay QR", description = """
            Sau lần đăng nhập đầu (qua QR), web có sẵn linked-device token trong
            localStorage. Khi user vào lại, web gọi endpoint này thay vì hiện QR
            mới — backend gửi push notification về mobile đã liên kết.

            Phase D.4 mới wire FCM/APNs. Hiện tại endpoint vẫn callable nhưng
            chỉ log warning; web vẫn nên có fallback hiển thị QR nếu push fail.
            """)
    @PostMapping("/push")
    public ResponseEntity<DataResponse<Void>> push(@Valid @RequestBody SessionPushRequest request) {
        sessionService.pushToLinkedDevice(request.sessionId(), request.linkedDeviceToken());
        return ResponseEntity.ok(
                DataResponse.<Void>builder()
                        .code(1000)
                        .message("Push triggered")
                        .build());
    }

    private static String parseBearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new AppException(ErrorCode.SESSION_NOT_FOUND, "missing or invalid Bearer header");
        }
        return authorization.substring(7).trim();
    }
}
