package com.magiclamp.phoenixkey_db.service;

import com.magiclamp.phoenixkey_db.dto.request.GuardianAddRequest;
import com.magiclamp.phoenixkey_db.dto.response.GuardianAddResponse;

/**
 * Service quản lý guardians.
 *
 * <p>
 * <b>Lưu ý:</b> Recovery approval (guardian ký lên Smart Contract) được thực
 * hiện trên
 * Blockchain (App → Cardano). PK_DB không lưu approval. Indexer chỉ sync trạng
 * thái TAAD
 * khi thấy DID đổi Controller.
 */
public interface GuardianService {

    /**
     * Thêm guardian cho user.
     *
     * <p>
     * Backend/NestJS verify {@code proofSignature} trước khi gọi.
     * Recovery (guardian ký lên chain) được xử lý trực tiếp trên Cardano.
     *
     * @param request chứa userDid + guardianDid + proofSignature
     * @return số guardian hiện tại
     */
    GuardianAddResponse addGuardian(GuardianAddRequest request);
}
