package com.magiclamp.phoenixkey_db.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response cho {@code GET /identity/health} (spec §9.5).
 *
 * <p>Dashboard banner ánh xạ state:
 * <ul>
 *   <li>{@code seedExported=false} → banner xanh "Tài khoản đang được bảo vệ"</li>
 *   <li>{@code seedExported=true} kèm {@code exportedAt} < 72h → banner vàng</li>
 *   <li>{@code exportedAt} ≥ 72h → banner đỏ "Bảo mật mức thấp"</li>
 *   <li>{@code activeKeyCount > 1} → user đa thiết bị</li>
 *   <li>{@code guardianCount} → số guardian active (gợi ý setup nếu = 0 sau 7 ngày)</li>
 * </ul>
 *
 * @param seedExported   true nếu đã từng trích xuất Seed Phrase
 * @param exportedAt     ISO-8601 timestamp lần export gần nhất; null nếu chưa
 * @param activeKeyCount số authorized_keys active của user
 * @param guardianCount  số guardian active của user
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentityHealthResponse(
        boolean seedExported,
        String exportedAt,
        long activeKeyCount,
        long guardianCount) {
}
