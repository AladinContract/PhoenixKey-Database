package com.magiclamp.phoenixkey_db.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO cho {@code POST /api/v1/guardians/approve-recovery}.
 *
 * <p>
 * Guardian bấm "Phê duyệt" để hỗ trợ khôi phục danh tính cho user.
 * PK_DB lưu vào bảng recovery_approvals. Khi đủ threshold → Indexer sync.
 */
public record GuardianApproveRecoveryRequest(
        /** DID của user đang cần khôi phục. */
        @NotBlank(message = "User DID is required") String userDid,

        /** DID của guardian đang phê duyệt (chính là caller). */
        @NotBlank(message = "Guardian DID is required") String guardianDid,

        /** PKH mà user mới muốn nhận quyền kiểm soát. */
        @NotBlank(message = "New controller PKH is required") String newControllerPkh,

        /** Chữ ký của guardian để phê duyệt. */
        @NotBlank(message = "Guardian signature is required") String guardianSignature) {
}
