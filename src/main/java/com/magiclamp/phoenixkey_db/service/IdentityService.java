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
     * Đăng ký identity mới.
     *
     * <p>
     * NestJS gọi sau khi App hoàn tất OTP verify.
     * PK_DB:
     * <ol>
     *   <li>Hash credential → blind_hash</li>
     *   <li>Tạo UUIDv7 cho user_id</li>
     *   <li>Insert users + auth_methods + authorized_keys</li>
     *   <li>Trả về user_id + user_did</li>
     * </ol>
     *
     * @param request chứa credential + pubkey + signature
     * @return userId + userDid
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
