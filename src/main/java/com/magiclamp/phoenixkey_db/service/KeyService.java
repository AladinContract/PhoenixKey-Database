package com.magiclamp.phoenixkey_db.service;

import com.magiclamp.phoenixkey_db.dto.request.KeyAuthorizeRequest;
import com.magiclamp.phoenixkey_db.dto.request.KeyRevokeRequest;
import com.magiclamp.phoenixkey_db.dto.response.KeyAuthorizeResponse;

/**
 * Service quản lý authorized keys — thêm và thu hồi thiết bị.
 */
public interface KeyService {

    /**
     * Thêm khóa/ thiết bị mới cho user.
     *
     * Zero-Trust: {@code added_by_signature} phải được verify trước khi insert.
     *
     * @param request chứa userDid + pubkey + signature
     * @return keyId của authorized_key vừa tạo
     */
    KeyAuthorizeResponse authorize(KeyAuthorizeRequest request);

    /**
     * Thu hồi khóa của user.
     *
     * Zero-Trust: {@code signature} phải được verify trước khi revoke.
     */
    void revoke(KeyRevokeRequest request);
}
