package com.magiclamp.phoenixkey_db.service;

import com.magiclamp.phoenixkey_db.dto.request.GuardianAddRequest;
import com.magiclamp.phoenixkey_db.dto.request.GuardianRemoveRequest;
import com.magiclamp.phoenixkey_db.dto.response.GuardianAddResponse;
import com.magiclamp.phoenixkey_db.dto.response.GuardianRemoveResponse;

/**
 * Service quản lý guardians.
 *
 * Lưu ý: Recovery approval (guardian ký lên Smart Contract) được thực
 * hiện trên
 * Blockchain (App → Cardano). PK_DB không lưu approval. Indexer chỉ sync trạng
 * thái TAAD
 * khi thấy DID đổi Controller.
 */
public interface GuardianService {

    /**
     * Thêm guardian cho user.
     *
     * Backend/NestJS verify {@code proofSignature} trước khi gọi.
     * Recovery (guardian ký lên chain) được xử lý trực tiếp trên Cardano.
     *
     * @param request chứa userDid + guardianDid + proofSignature
     * @return số guardian hiện tại
     */
    GuardianAddResponse addGuardian(GuardianAddRequest request);

    /**
     * Xóa một guardian của user (soft revoke).
     *
     * Sau khi remove, user có thể thêm guardian khác thay thế.
     * Số guardian còn lại được trả về để App kiểm tra ngưỡng tối thiểu.
     *
     * @param request chứa userDid + guardianDid
     * @return số guardian còn lại
     * @throws AppException(ErrorCode.GUARDIAN_NOT_FOUND) nếu guardian không tồn tại hoặc đã bị revoke
     */
    GuardianRemoveResponse removeGuardian(GuardianRemoveRequest request);
}
