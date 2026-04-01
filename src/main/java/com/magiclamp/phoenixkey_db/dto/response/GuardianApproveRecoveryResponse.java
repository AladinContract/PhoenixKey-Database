package com.magiclamp.phoenixkey_db.dto.response;

/**
 * Response DTO cho {@code POST /api/v1/guardians/approve-recovery}.
 *
 * @param approvalCount Số guardian đã phê duyệt
 * @param required      Số guardian cần để hoàn tất recovery
 */
public record GuardianApproveRecoveryResponse(
        int approvalCount,
        int required) {
}
