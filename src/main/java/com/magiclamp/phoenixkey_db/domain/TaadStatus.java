package com.magiclamp.phoenixkey_db.domain;

/**
 * Enum ánh xạ với PostgreSQL TYPE taad_status.
 *
 * <p>
 * Ánh xạ tuyệt đối với định nghĩa trong migration V4:
 * 
 * <pre>
 * CREATE TYPE taad_status AS ENUM ('ACTIVE', 'RECOVERING', 'MIGRATED');
 * </pre>
 */
public enum TaadStatus {
    /** Bình thường: chủ DID đang kiểm soát tài khoản. */
    ACTIVE,

    /**
     * Đang trong quá trình khôi phục:
     * - Guardian đã kích hoạt recovery
     * - Time-lock đang đếm ngược
     * - Chủ cũ có thể Cancel trong cửa sổ time-lock
     */
    RECOVERING,

    /**
     * Đã di cư: DID đã chuyển sang ví mới.
     * Dùng cho audit trail — không có logic nghiệp vụ ở DB layer.
     */
    MIGRATED
}
