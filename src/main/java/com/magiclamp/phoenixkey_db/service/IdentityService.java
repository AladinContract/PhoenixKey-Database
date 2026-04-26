package com.magiclamp.phoenixkey_db.service;

import com.magiclamp.phoenixkey_db.dto.request.IdentityRegisterRequest;
import com.magiclamp.phoenixkey_db.dto.response.IdentityPubkeyResponse;
import com.magiclamp.phoenixkey_db.dto.response.IdentityRegisterResponse;
import com.magiclamp.phoenixkey_db.dto.response.IdentityStatusResponse;

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
}
