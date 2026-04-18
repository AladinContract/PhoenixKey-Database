package com.magiclamp.phoenixkey_db.domain;

/**
 * [V1.5] Enum ánh xạ với PostgreSQL TYPE key_origin_type.
 *
 * Biết nguồn gốc Hardware Key để SDK quyết định có tìm mảnh
 * trên LampNet khi Recovery hay không.
 *
 * Ánh xạ tuyệt đối với định nghĩa trong migration V2:
 *
 * CREATE TYPE key_origin_type AS ENUM (
 *   'SECURE_ENCLAVE', -- Key sinh trong Secure Enclave/TEE — có mảnh trên LampNet
 *   'IMPORTED_BIP39', -- Seed phrase nhập từ ngoài — KHÔNG có mảnh LampNet
 *   'DERIVED_CHILD'   -- Key derive từ seed gốc — dự phòng
 * );
 */
public enum KeyOriginType {

    /**
     * Key sinh trong Secure Enclave/TEE chip.
     * SDK tìm mảnh khóa trên LampNet khi Recovery.
     */
    SECURE_ENCLAVE,

    /**
     * Seed phrase nhập từ ví ngoài (Yoroi, Eternl...).
     * KHÔNG có mảnh trên LampNet.
     * SDK yêu cầu user nhập lại seed khi Recovery.
     */
    IMPORTED_BIP39,

    /**
     * Key derive từ seed gốc bằng BIP-32.
     * Dự phòng cho tương lai.
     */
    DERIVED_CHILD
}
