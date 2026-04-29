package com.magiclamp.phoenixkey_db.service.key;

import com.magiclamp.phoenixkey_db.dto.key.KeyAuthorizeRequest;
import com.magiclamp.phoenixkey_db.dto.key.KeyRevokeRequest;
import com.magiclamp.phoenixkey_db.dto.key.KeyRotateRequest;
import com.magiclamp.phoenixkey_db.dto.key.KeyAuthorizeResponse;
import com.magiclamp.phoenixkey_db.dto.key.KeyRotationResponse;

/**
 * Service quản lý authorized keys — thêm, thu hồi, xoay khóa thiết bị.
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

    /**
     * Xoay khóa: thay public key owner bằng key mới qua Cardano updateDID + cập
     * nhật {@code authorized_keys} (revoke key cũ + insert key mới active).
     *
     * Spec §11. MVP: fee wallet ký Cardano tx (vi phạm Zero-Trust nhẹ — Phase H
     * sẽ enforce old key sign như required signer).
     */
    KeyRotationResponse rotate(KeyRotateRequest request);
}
