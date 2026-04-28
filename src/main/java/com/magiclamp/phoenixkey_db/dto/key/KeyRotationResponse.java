package com.magiclamp.phoenixkey_db.dto.key;

/**
 * Response cho {@code POST /api/v1/keys/rotate}.
 *
 * @param txHash       Cardano tx hash của updateDID transaction (verify trên
 *                     Cardanoscan / Blockfrost)
 * @param newKeyId     UUID của row mới trong {@code authorized_keys}
 */
public record KeyRotationResponse(String txHash, String newKeyId) {
}
