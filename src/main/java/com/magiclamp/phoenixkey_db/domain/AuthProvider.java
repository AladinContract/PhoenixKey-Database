package com.magiclamp.phoenixkey_db.domain;

/**
 * Enum ánh xạ với PostgreSQL TYPE auth_provider.
 *
 * <p>
 * Enum này phải khớp tuyệt đối với định nghĩa trong migration V1:
 * 
 * <pre>
 * CREATE TYPE auth_provider AS ENUM ('google', 'apple', 'phone');
 * </pre>
 */
public enum AuthProvider {
    GOOGLE,
    APPLE,
    PHONE
}
