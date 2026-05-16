package com.magiclamp.phoenixkey_db.service.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magiclamp.phoenixkey_db.dto.activation.ActivationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * In-memory SSE event bus for activation lifecycle. Each activation_id may have
 * multiple subscribers (web user + Genie's app).
 *
 * <p><b>Multi-instance note:</b> SSE state hiện in-memory. Khi scale ≥ 2 server
 * instance, cần thay bằng Redis Pub/Sub broadcaster. Xem
 * {@code SseEmitterRegistry} hiện có cho session flow để dùng cùng pattern.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivationEventBus {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;  // 30 phút

    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter register(String activationId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        subscribers.computeIfAbsent(activationId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> unregister(activationId, emitter));
        emitter.onTimeout(() -> unregister(activationId, emitter));
        emitter.onError(e -> unregister(activationId, emitter));

        // Send initial ping so client knows connection is open
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            log.warn("SSE initial ping failed for activation {}: {}", activationId, e.getMessage());
        }

        log.debug("SSE subscriber registered for activation {}", activationId);
        return emitter;
    }

    public void publish(String activationId, ActivationEvent event) {
        List<SseEmitter> emitters = subscribers.get(activationId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No SSE subscribers for activation {} — event {} dropped", activationId, event.type());
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(event.data());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize activation event: {}", e.getMessage());
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.type())
                        .data(json));
            } catch (IOException e) {
                log.debug("SSE send failed (subscriber gone): {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }
        log.info("SSE event '{}' published to {} subscribers for activation {}",
                event.type(), emitters.size(), activationId);
    }

    private void unregister(String activationId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(activationId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                subscribers.remove(activationId);
            }
        }
    }
}
