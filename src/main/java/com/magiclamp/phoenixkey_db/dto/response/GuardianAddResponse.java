package com.magiclamp.phoenixkey_db.dto.response;

/**
 * Response DTO cho {@code POST /api/v1/guardians/add}.
 *
 * @param guardianCount Số guardian hiện tại của user
 */
public record GuardianAddResponse(
        int guardianCount) {
}
