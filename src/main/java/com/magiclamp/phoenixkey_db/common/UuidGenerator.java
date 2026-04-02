package com.magiclamp.phoenixkey_db.common;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.github.f4b6a3.uuid.UuidCreator;

import lombok.extern.slf4j.Slf4j;

/**
 * UUIDv7 generator — timestamp-prefixed UUID.
 *
 * Dùng thay vì UUID.randomUUID() (v4 ngẫu nhiên).
 * UUIDv7 có timestamp prefix → B-Tree insert luôn ở cuối → hiệu năng cực cao.
 *
 * Lib: {@code com.github.f4b6a3:uuid-creator}
 */
@Component
@Slf4j
public class UuidGenerator {

    /**
     * Tạo UUIDv7 mới.
     *
     * @return UUIDv7 với timestamp prefix
     */
    public UUID create() {
        return UuidCreator.getTimeOrdered();
    }
}
