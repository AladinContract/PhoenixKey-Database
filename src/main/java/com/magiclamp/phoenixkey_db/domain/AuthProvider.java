package com.magiclamp.phoenixkey_db.domain;

/**
 * Enum ánh xạ với PostgreSQL TYPE auth_provider.
 *
 * Enum này phải khớp tuyệt đối với định nghĩa trong migration V1:
 * 
 * CREATE TYPE auth_provider AS ENUM ('GOOGLE', 'APPLE', 'PHONE');
 */
public enum AuthProvider {
    GOOGLE,
    APPLE,
    PHONE
}
