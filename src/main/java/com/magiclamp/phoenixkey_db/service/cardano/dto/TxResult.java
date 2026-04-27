package com.magiclamp.phoenixkey_db.service.cardano.dto;

/**
 * Kết quả thao tác Cardano: CREATE / UPDATE / DEACTIVATE DID.
 *
 * @param txHash    Cardano transaction hash (64 hex chars)
 * @param did       DID string {@code did:cardano:<network>:<txHash>}
 * @param operation "CREATE" | "UPDATE" | "DEACTIVATE"
 */
public record TxResult(String txHash, String did, String operation) {
}
