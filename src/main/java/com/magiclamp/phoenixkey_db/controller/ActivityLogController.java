package com.magiclamp.phoenixkey_db.controller;

import com.magiclamp.phoenixkey_db.common.DataResponse;
import com.magiclamp.phoenixkey_db.domain.ActivityLog;
import com.magiclamp.phoenixkey_db.dto.activity.ActivityLogPage;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.ActivityLogRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import com.magiclamp.phoenixkey_db.security.JwtService;
import com.magiclamp.phoenixkey_db.security.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Activity logs endpoint (spec §10).
 *
 * <p>Cursor-based pagination, filter theo action_type, range 7d/30d/all.
 * Zero-PII: userId chỉ show 8 char đầu, ip_hash ẩn 2 octet cuối.</p>
 */
@RestController
@RequestMapping("/activity-logs")
@RequiredArgsConstructor
@Tag(name = "Activity Logs", description = "Audit trail (spec §10)")
public class ActivityLogController {

    private static final int MAX_LIMIT = 100;
    private static final String IP_HASH_KEY = "ip_hash";

    private final JwtService jwtService;
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    @Operation(summary = "List activity logs (cursor pagination)", description = """
            Trả về log của user hiện tại (xác thực bằng session_token).

            Cursor pagination: client pass `cursor` = id của item cuối trang trước.
            Trang đầu: bỏ qua `cursor`. Khi `nextCursor` là null → hết data.

            **Filter:**
            - `filter`: action name (vd `login_success`, `key_rotated`); null = tất cả
            - `range`: `7d` | `30d` | `all` (mặc định `all`)
            - `limit`: 1-100 (mặc định 20)

            **Bearer:** session_token (24h TTL).
            """)
    @ApiResponse(responseCode = "200", description = "Logs page", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    @ApiResponse(responseCode = "403", description = "Invalid session token")
    @GetMapping
    public ResponseEntity<DataResponse<ActivityLogPage>> list(
            @RequestHeader("Authorization") String authorization,
            @Parameter(description = "1-100") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "UUID id của item cuối trang trước") @RequestParam(required = false) String cursor,
            @Parameter(description = "action name filter") @RequestParam(required = false) String filter,
            @Parameter(description = "7d | 30d | all") @RequestParam(defaultValue = "all") String range) {

        String userDid = userDidFromBearer(authorization);
        UUID userId = userRepository.findByUserDid(userDid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_DID_NOT_FOUND))
                .getId();

        int pageSize = Math.clamp(limit, 1, MAX_LIMIT);
        UUID cursorUuid = cursor != null && !cursor.isBlank() ? UUID.fromString(cursor) : null;
        OffsetDateTime since = parseRange(range);

        List<ActivityLog> rows = activityLogRepository.queryPage(
                userId, filter, since, cursorUuid, PageRequest.of(0, pageSize));

        List<ActivityLogPage.ActivityLogItem> items = rows.stream()
                .map(ActivityLogController::toItem)
                .toList();

        // nextCursor = id của item cuối nếu trang đầy; null nếu trang cuối.
        String nextCursor = (rows.size() == pageSize)
                ? rows.get(rows.size() - 1).getId().toString()
                : null;

        ActivityLogPage page = new ActivityLogPage(items, nextCursor);
        return ResponseEntity.ok(
                DataResponse.<ActivityLogPage>builder()
                        .code(1000)
                        .message("Activity logs")
                        .result(page)
                        .build());
    }

    private static OffsetDateTime parseRange(String range) {
        if (range == null) {
            return null;
        }
        return switch (range.toLowerCase()) {
            case "7d" -> OffsetDateTime.now().minusDays(7);
            case "30d" -> OffsetDateTime.now().minusDays(30);
            default -> null; // "all" hoặc không nhận diện → không filter
        };
    }

    /** Zero-PII transform: userId truncate 8 char, metadata.ip_hash mask 2 octet cuối. */
    private static ActivityLogPage.ActivityLogItem toItem(ActivityLog row) {
        String shortUserId = row.getUserId() != null
                ? row.getUserId().toString().substring(0, 8)
                : null;
        var maskedMeta = maskIpInMetadata(row.getMetadata());
        return new ActivityLogPage.ActivityLogItem(
                row.getId().toString(),
                shortUserId,
                row.getAction(),
                maskedMeta,
                row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
    }

    private static java.util.Map<String, Object> maskIpInMetadata(java.util.Map<String, Object> metadata) {
        if (metadata == null || !metadata.containsKey(IP_HASH_KEY)) {
            return metadata;
        }
        // Nếu metadata có ip_hash thật → mask. Hash đã là SHA-256 (Zero-PII)
        // nhưng vẫn show partial cho dev debug, full cho audit team riêng biệt.
        var copy = new java.util.HashMap<>(metadata);
        Object ip = copy.get(IP_HASH_KEY);
        if (ip instanceof String s && s.length() > 8) {
            copy.put(IP_HASH_KEY, s.substring(0, 8) + "...");
        }
        return copy;
    }

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
