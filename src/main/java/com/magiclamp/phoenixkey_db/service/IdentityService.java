package com.magiclamp.phoenixkey_db.service;

import com.magiclamp.phoenixkey_db.dto.request.IdentityRegisterRequest;
import com.magiclamp.phoenixkey_db.dto.request.UserDidUpdateRequest;
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
     * NestJS gọi sau khi App hoàn tất OTP verify.
     * PK_DB:
     *   1. Hash credential → blind_hash
     *   2. Tạo UUIDv7 cho user_id
     *   3. Insert users + auth_methods + authorized_keys
     *   4. Trả về user_id + user_did
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

    /**
     * Update userDid sau khi NestJS mint DID trên Cardano.
     *
     * Sau khi App đăng ký → PK_DB tạo user với userDid = "pending".
     * NestJS mint DID xong → gọi endpoint này để update DID thực sự.
     *
     * @param request chứa userId + userDid đã mint
     * @throws AppException(ErrorCode.USER_NOT_FOUND) nếu userId không tồn tại
     * @throws AppException(ErrorCode.USER_DID_ALREADY_EXISTS) nếu DID đã được gán cho user khác
     */
    void updateUserDid(UserDidUpdateRequest request);
}
