// ── UsernameController.java ───────────────────────────────────────────────────
// src/main/java/com/magiclamp/phoenixkey_db/controller/UsernameController.java

package com.magiclamp.phoenixkey_db.controller;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.dto.username.UsernameSetRequest;
import com.magiclamp.phoenixkey_db.dto.username.UsernameSetResponse;
import com.magiclamp.phoenixkey_db.dto.username.UsernameResolveResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.security.JwtService;
import com.magiclamp.phoenixkey_db.security.JwtServiceImpl;
import com.magiclamp.phoenixkey_db.service.username.UsernameService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Username endpoints — đặt username + resolve username → DID.
 *
 * <p>Username KHÔNG phải auth credential — chỉ là lookup shortcut.
 * Flow đăng nhập bằng username:
 * <ol>
 *   <li>Web: {@code GET /identity/by-username/{username}} → nhận DID</li>
 *   <li>Web: init session với DID đó → hiển thị QR</li>
 *   <li>Mobile: approve QR bằng Hardware Key (biometric)</li>
 * </ol>
 */
@RestController
@RequestMapping("/identity")
@RequiredArgsConstructor
@Tag(name = "Username", description = "Đặt username + resolve username → DID")
public class UsernameController {

    private final UsernameService usernameService;
    private final JwtService jwtService;

    /**
     * Đặt hoặc đổi username.
     *
     * <p>Yêu cầu Bearer session_token (24h TTL từ session approve).
     * DID được extract từ JWT sub claim — user chỉ đổi được username của chính mình.
     *
     * <p>Rules:
     * <ul>
     *   <li>3–32 ký tự: [a-z0-9_]</li>
     *   <li>Không phân biệt hoa thường (lưu lowercase)</li>
     *   <li>Lần đầu đặt: không có cooldown</li>
     *   <li>Đổi lần tiếp: cooldown 30 ngày</li>
     *   <li>Reserved names bị từ chối (admin, system, phoenixkey, ...)</li>
     * </ul>
     */
    @Operation(
            summary = "Đặt hoặc đổi username",
            description = """
                    Đặt username cho identity. Username là lookup shortcut — không phải auth credential.

                    **Bearer:** session_token (lấy từ session approve flow).

                    **Rules:**
                    - 3–32 ký tự: `[a-z0-9_]` (chữ thường, số, dấu gạch dưới)
                    - Case-insensitive (lưu lowercase)
                    - Lần đầu: không cooldown
                    - Đổi lần sau: cooldown 30 ngày
                    - Reserved names (admin, system, ...) bị từ chối

                    **Sau khi đặt:**
                    Web có thể dùng `GET /identity/by-username/{username}` để resolve → DID,
                    rồi init session cho user đó.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Username đã đặt thành công",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "400", description = "Username không hợp lệ (format, reserved name)",
            content = @Content)
    @ApiResponse(responseCode = "403", description = "Invalid session token", content = @Content)
    @ApiResponse(responseCode = "409", description = "Username đã tồn tại hoặc đang trong cooldown",
            content = @Content)
    @PutMapping("/username")
    public ResponseEntity<DataResponse<UsernameSetResponse>> setUsername(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody UsernameSetRequest request) {

        String userDid = userDidFromBearer(authorization);
        UsernameSetResponse result = usernameService.setUsername(userDid, request);

        return ResponseEntity.ok(
                DataResponse.<UsernameSetResponse>builder()
                        .code(1000)
                        .message("Username set successfully")
                        .result(result)
                        .build());
    }

    /**
     * Resolve username → DID. Public endpoint, không cần auth.
     *
     * <p>Web dùng để lookup DID trước khi init session.
     */
    @Operation(
            summary = "Resolve username → DID (public)",
            description = """
                    Tra cứu DID từ username. Public endpoint — không cần auth.

                    **Use case:** Web nhập username → lookup DID → init QR session cho DID đó.

                    **Không trả thêm thông tin:** Chỉ trả username (normalized) + userDid.
                    """
    )
    @ApiResponse(responseCode = "200", description = "DID của username",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "404", description = "Username không tồn tại", content = @Content)
    @GetMapping("/by-username/{username}")
    public ResponseEntity<DataResponse<UsernameResolveResponse>> resolveByUsername(
            @Parameter(description = "Username cần resolve", example = "chị_oanh")
            @PathVariable("username") String username) {

        UsernameResolveResponse result = usernameService.resolveByUsername(username);

        return ResponseEntity.ok(
                DataResponse.<UsernameResolveResponse>builder()
                        .code(1000)
                        .message("Username resolved")
                        .result(result)
                        .build());
    }

    // ─── JWT helper (pattern từ IdentityController) ─────────────────────────

    private String userDidFromBearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "missing or invalid Bearer header");
        }
        String token = authorization.substring(7).trim();
        Claims claims = jwtService.parseAndVerify(token);
        if (!JwtServiceImpl.TYPE_SESSION.equals(claims.get("type", String.class))) {
            throw new AppException(ErrorCode.SIGNATURE_INVALID, "not a session token");
        }
        return claims.getSubject();
    }
}
