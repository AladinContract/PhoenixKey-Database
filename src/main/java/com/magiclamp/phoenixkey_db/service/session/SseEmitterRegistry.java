package com.magiclamp.phoenixkey_db.service.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * In-memory registry cho SSE emitters keyed by sessionId.
 *
 * <p>Khi web mở SSE stream cho session, emitter được lưu ở đây. Mobile approve
 * → service push qua {@link #emit(String, String, Object)} → web nhận event.</p>
 *
 * <p>MVP single-instance: nếu deploy nhiều replica, mỗi replica có map riêng,
 * web có thể connect replica khác mobile callback → miss event. Phase H sẽ
 * dùng Redis Pub/Sub để fan-out cross-replica.</p>
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Đăng ký emitter cho sessionId. Nếu đã có emitter cũ → close nó (web reconnect).
     */
    public SseEmitter register(String sessionId, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        SseEmitter old = emitters.put(sessionId, emitter);
        if (old != null) {
            log.debug("SSE registry: replacing existing emitter for session={}", sessionId);
            try {
                old.complete();
            } catch (Exception ignored) {
                // emitter có thể đã bị close phía client — bỏ qua
            }
        }

        BiConsumer<String, Throwable> cleanup = (reason, error) -> {
            emitters.remove(sessionId, emitter);
            log.debug("SSE registry: removed emitter for session={} reason={}", sessionId, reason);
        };
        emitter.onCompletion(() -> cleanup.accept("completion", null));
        emitter.onTimeout(() -> cleanup.accept("timeout", null));
        emitter.onError(err -> cleanup.accept("error", err));

        log.info("SSE registry: registered emitter for session={}, total={}", sessionId, emitters.size());
        return emitter;
    }

    /**
     * Push event tới web client của sessionId. Trả false nếu không có emitter
     * (web chưa connect hoặc đã disconnect — caller có thể fallback poll status).
     */
    public boolean emit(String sessionId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            log.debug("SSE emit: no emitter for session={} (web not connected)", sessionId);
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException e) {
            log.warn("SSE emit failed for session={}: {}", sessionId, e.getMessage());
            emitters.remove(sessionId, emitter);
            return false;
        }
    }

    /**
     * Heartbeat ping cho tất cả emitter — gửi SSE comment ":ping" để giữ proxy
     * enterprise không kill connection (spec §15.1, default timeout 60-90s).
     */
    public void pingAll() {
        if (emitters.isEmpty()) {
            return;
        }
        emitters.forEach((sessionId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                log.debug("SSE ping failed for session={}: {}", sessionId, e.getMessage());
                emitters.remove(sessionId, emitter);
            }
        });
    }

    /** Force-close emitter (vd: session expire, mobile reject). */
    public void close(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // emitter đã close
            }
        }
    }

    public int size() {
        return emitters.size();
    }
}
