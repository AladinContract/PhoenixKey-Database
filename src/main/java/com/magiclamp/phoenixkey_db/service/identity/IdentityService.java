package com.magiclamp.phoenixkey_db.service.identity;

import com.magiclamp.phoenixkey_db.dto.identity.IdentityRegisterRequest;
import com.magiclamp.phoenixkey_db.dto.identity.IdentityHealthResponse;
import com.magiclamp.phoenixkey_db.dto.identity.IdentityPubkeyResponse;
import com.magiclamp.phoenixkey_db.dto.identity.IdentityRegisterResponse;
import com.magiclamp.phoenixkey_db.dto.identity.IdentityStatusResponse;

/**
 * Service xử lý identity — register, tra cứu pubkey, tra cứu status.
 */
public interface IdentityService {

    /**
     * Đăng ký identity mới — 1-step (mint DID Cardano + insert user trong cùng transaction).
     *
     * Flow:
     *   1. Verify Genesis signature từ Hardware Key (proof có private key)
     *   2. Publish DID Document lên Cardano qua CardanoService
     *   3. Insert users + authorized_keys với userDid lấy từ tx hash
     *
     * @param request publicKeyHex + keyOrigin + keyRole + addedBySignature
     * @return userId + userDid + txHash
     */
    IdentityRegisterResponse register(IdentityRegisterRequest request);

    /**
     * Lấy public key của một user qua DID.
     *
     * @param userDid DID string
     * @return public key hex + key role
     */
    IdentityPubkeyResponse getPubkey(String userDid);

    /**
     * Lấy trạng thái TAAD của một user qua DID.
     *
     * @param userDid DID string
     * @return status, controller PKH, sequence, deadline
     */
    IdentityStatusResponse getStatus(String userDid);

    /**
     * Health snapshot cho dashboard banner (spec §9.5).
     *
     * @param userDid DID string từ session_token
     * @return seedExported + activeKeyCount + guardianCount
     */
    IdentityHealthResponse getHealth(String userDid);
}
