package com.magiclamp.phoenixkey_db.dto.identity;

/**
 * Response DTO cho {@code POST /api/v1/identity/register}.
 *
 * @param userId  UUIDv7 của user trong PhoenixKey-Server
 * @param userDid DID đã mint trên Cardano: {@code did:cardano:<network>:<txHash>}
 * @param txHash  Cardano transaction hash (verify được trên Cardanoscan)
 */
public record IdentityRegisterResponse(
        String userId,
        String userDid,
        String txHash) {
}
