package com.magiclamp.phoenixkey_db.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request để update userDid sau khi NestJS mint DID trên Cardano.
 *
 * Flow:
 * 1. App đăng ký → PK_DB tạo user với userDid = "pending"
 * 2. NestJS mint DID trên Cardano xong
 * 3. NestJS gọi endpoint này để update userDid thực sự
 *
 * @param userId UUID của user (từ step 1)
 * @param userDid DID thực sự đã mint trên Cardano
 */
public record UserDidUpdateRequest(
        @jakarta.validation.constraints.NotNull(message = "userId is required")
        java.util.UUID userId,

        @NotBlank(message = "userDid is required")
        String userDid
) {}
