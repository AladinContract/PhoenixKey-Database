package com.magiclamp.phoenixkey_db.scheduled;

import com.magiclamp.phoenixkey_db.service.activation.ActivationServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs every minute to mark expired activations and release their Genie slots.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivationSweeper {

    private final ActivationServiceImpl activationService;

    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        try {
            activationService.sweepExpired();
        } catch (Exception e) {
            log.error("Activation sweep failed: {}", e.getMessage());
        }
    }
}
