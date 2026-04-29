package com.magiclamp.phoenixkey_db.dto.identity;

/**
 * Response DTO cho {@code GET /api/v1/identity/{did}/pubkey}.
 *
 * @param publicKeyHex Public key hex của owner key
 * @param keyRole      Vai trò: owner | farm_manager | read_only
 */
public record IdentityPubkeyResponse(
		String publicKeyHex,
		String keyRole) {
}
