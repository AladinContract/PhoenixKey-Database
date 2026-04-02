package com.magiclamp.phoenixkey_db.dto.response;

/**
 * Response DTO cho {@code POST /api/v1/guardians/remove}.
 *
 * @param guardianCount Số guardian còn lại của user
 */
public record GuardianRemoveResponse(
        int guardianCount) {
}
