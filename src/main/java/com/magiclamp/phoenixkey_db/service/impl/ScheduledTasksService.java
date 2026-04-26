package com.magiclamp.phoenixkey_db.service.impl;

import com.magiclamp.phoenixkey_db.service.NonceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasksService {

    private final NonceService nonceService;

    @Scheduled(fixedRate = 3_600_000) // mỗi giờ
    public void cleanupExpiredNonces() {
        int deleted = nonceService.deleteExpired();
        log.info("[Scheduler] Deleted {} expired nonces", deleted);
    }
}
