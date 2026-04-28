package com.magiclamp.phoenixkey_db.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Response cho {@code GET /api/v1/activity-logs}.
 *
 * <p>Cursor-based pagination — frontend pass {@code nextCursor} của response
 * vào {@code ?cursor=} của request kế tiếp.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivityLogPage(
        List<ActivityLogItem> logs,
        String nextCursor) {

    /**
     * Item — Zero-PII (spec §10.1):
     * - {@code userId}: 8 char đầu UUID
     * - {@code metadata.ip_hash}: ẩn 2 octet cuối (vd "103.xxx.x.x")
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActivityLogItem(
            String id,
            String userId,
            String action,
            Map<String, Object> metadata,
            String createdAt) {
    }
}
