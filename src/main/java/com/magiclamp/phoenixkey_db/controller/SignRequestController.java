package com.magiclamp.phoenixkey_db.controller;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.dto.sign.SignApproveRequest;
import com.magiclamp.phoenixkey_db.dto.sign.SignRequestCreateRequest;
import com.magiclamp.phoenixkey_db.dto.sign.SignRequestCreateResponse;
import com.magiclamp.phoenixkey_db.dto.sign.SignRequestPayload;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.service.sign.SignRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign request relay endpoints (spec §7).
 *
 * <p>Web tạo request, mobile fetch + approve, web nhận signature qua SSE.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sign Request", description = "Web ↔ mobile sign relay (spec §7)")
public class SignRequestController {

    private final SignRequestService signRequestService;

    @Operation(summary = "Create sign request — web tạo yêu cầu ký", description = """
            Yêu cầu Bearer session_token (issued ở /auth/session/{id}/approve).
            Server lookup userDid từ token → push notification mobile (Phase D.4)
            → mobile fetch payload qua GET /sign/request/{id}.
            """)
    @ApiResponse(responseCode = "200", description = "Sign request created", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "403", description = "Invalid session token")
    @PostMapping("/sign/request")
    public ResponseEntity<DataResponse<SignRequestCreateResponse>> create(
            @Valid @RequestBody SignRequestCreateRequest request,
            @RequestHeader("Authorization") String authorization) {
        SignRequestCreateResponse result = signRequestService.create(request, parseBearer(authorization));
        return ResponseEntity.ok(
                DataResponse.<SignRequestCreateResponse>builder()
                        .code(1000)
                        .message("Sign request created")
                        .result(result)
                        .build());
    }

    @Operation(summary = "Get sign request — mobile fetch payload sau khi nhận push", description = """
            Mobile sau khi nhận push notification (chỉ chứa requestId), fetch chi
            tiết qua endpoint này để hiển thị intent cho user. Spec §15.5: payload
            không nằm trong push để tránh leak qua notification service.
            """)
    @GetMapping("/sign/request/{id}")
    public ResponseEntity<DataResponse<SignRequestPayload>> get(
            @Parameter(description = "request_id") @PathVariable("id") String requestId) {
        SignRequestPayload result = signRequestService.get(requestId);
        return ResponseEntity.ok(
                DataResponse.<SignRequestPayload>builder()
                        .code(1000)
                        .message("Sign request payload")
                        .result(result)
                        .build());
    }

    @Operation(summary = "Approve sign request — mobile gọi sau khi ký intent", description = """
            Mobile sau khi user xem intent + biometric unlock + ký intent JSON
            (canonical form: keys sorted, no whitespace). Server verify →
            consume nonce → emit SSE event "signed" tới web với signature.
            """)
    @ApiResponse(responseCode = "200", description = "Signature verified, web notified")
    @ApiResponse(responseCode = "403", description = "Signature invalid")
    @ApiResponse(responseCode = "404", description = "Sign request not found / expired")
    @ApiResponse(responseCode = "409", description = "Nonce đã dùng")
    @PostMapping("/sign/{id}/approve")
    public ResponseEntity<DataResponse<Void>> approve(
            @PathVariable("id") String requestId,
            @Valid @RequestBody SignApproveRequest request) {
        signRequestService.approve(requestId, request);
        return ResponseEntity.ok(
                DataResponse.<Void>builder()
                        .code(1000)
                        .message("Sign request approved")
                        .build());
    }

    @Operation(summary = "Cancel sign request — web huỷ request đang chờ", description = """
            Bearer session_token bắt buộc — chỉ owner của request mới cancel được.
            SSE emit event "cancelled" tới web (lý do: tab khác cancel, user đổi ý...).
            """)
    @PostMapping("/sign/{id}/cancel")
    public ResponseEntity<DataResponse<Void>> cancel(
            @PathVariable("id") String requestId,
            @RequestHeader("Authorization") String authorization) {
        signRequestService.cancel(requestId, parseBearer(authorization));
        return ResponseEntity.ok(
                DataResponse.<Void>builder()
                        .code(1000)
                        .message("Sign request cancelled")
                        .build());
    }

    private static String parseBearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "missing or invalid Bearer header");
        }
        return authorization.substring(7).trim();
    }
}
