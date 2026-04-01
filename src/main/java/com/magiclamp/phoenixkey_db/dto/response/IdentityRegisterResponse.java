package com.magiclamp.phoenixkey_db.dto.response;

/**
 * Response DTO cho {@code POST /api/v1/identity/register}.
 *
 * @param userId  UUIDv7 của user trong PK_DB
 * @param userDid DID trên Blockchain — sau khi NestJS mint
 */
public record IdentityRegisterResponse(
        String userId,
        String userDid) {
}
