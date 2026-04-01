package com.magiclamp.phoenixkey_db.dto.response;

import com.magiclamp.phoenixkey_db.domain.TaadStatus;

/**
 * Response DTO cho {@code GET /api/v1/identity/{did}/status}.
 *
 * @param status           Trạng thái TAAD: ACTIVE | RECOVERING | MIGRATED
 * @param controllerPkh    Public Key Hash đang kiểm soát DID
 * @param sequence         Sequence số thứ tự giao dịch
 * @param recoveryDeadline Deadline khôi phục (null nếu ACTIVE)
 */
public record IdentityStatusResponse(
        TaadStatus status,
        String controllerPkh,
        Long sequence,
        String recoveryDeadline) {
}
