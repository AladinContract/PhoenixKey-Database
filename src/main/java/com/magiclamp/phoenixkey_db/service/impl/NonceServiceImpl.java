package com.magiclamp.phoenixkey_db.service.impl;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.magiclamp.phoenixkey_db.domain.UsedNonce;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.UsedNonceRepository;
import com.magiclamp.phoenixkey_db.service.NonceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NonceServiceImpl implements NonceService {

    private final UsedNonceRepository usedNonceRepository;

    @Override
    @Transactional
    public void validateAndConsume(String nonce, String userDid, Duration ttl) {
        if (usedNonceRepository.existsByNonceAndUserDid(nonce, userDid)) {
            log.warn("Nonce replay detected: nonce={}, userDid={}", nonce, userDid);
            throw new AppException(ErrorCode.NONCE_ALREADY_USED);
        }

        UsedNonce record = UsedNonce.builder()
                .nonce(nonce)
                .userDid(userDid)
                .usedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plus(ttl))
                .build();
        usedNonceRepository.save(record);

        log.debug("Nonce consumed: nonce={}, userDid={}, ttl={}", nonce, userDid, ttl);
    }

    @Override
    @Transactional
    public int deleteExpired() {
        int deleted = usedNonceRepository.deleteExpired(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("Expired nonces cleaned: {} records deleted", deleted);
        }
        return deleted;
    }
}
