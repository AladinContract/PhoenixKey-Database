package com.magiclamp.phoenixkey_db.service.scheduling;

import com.magiclamp.phoenixkey_db.service.nonce.NonceService;
import com.magiclamp.phoenixkey_db.service.session.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasksService {

    private final NonceService nonceService;
    private final SseEmitterRegistry sseRegistry;

    @Scheduled(fixedRate = 3_600_000) // mỗi giờ
    public void cleanupExpiredNonces() {
        int deleted = nonceService.deleteExpired();
        log.info("[Scheduler] Deleted {} expired nonces", deleted);
    }

    /**
     * Heartbeat ping cho tất cả SSE emitter đang mở. Spec §15.1 yêu cầu giữ
     * proxy enterprise (Zscaler/BlueCoat) không kill connection sau 60-90s
     * idle. Mỗi 30s đủ buffer cho proxy timeout phổ biến nhất.
     */
    @Scheduled(fixedRate = 30_000)
    public void sseHeartbeat() {
        if (sseRegistry.size() > 0) {
            sseRegistry.pingAll();
        }
    }
}
