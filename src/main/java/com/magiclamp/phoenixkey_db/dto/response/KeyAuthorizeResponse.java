package com.magiclamp.phoenixkey_db.dto.response;

/**
 * Response DTO cho {@code POST /api/v1/keys/authorize}.
 *
 * @param keyId UUID của authorized_key vừa tạo
 */
public record KeyAuthorizeResponse(
        String keyId) {
}
