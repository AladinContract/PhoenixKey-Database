package com.magiclamp.phoenixkey_db.service.indexer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Indexer Worker — polls Blockfrost for TAAD UTxO changes and pushes them to
 * {@link IndexerService#syncTaad}.
 *
 * <p><b>TODO (Lợi):</b> wire BloxBean BlockService + parse inline datum.
 * Disabled by default; enable with {@code phoenixkey.indexer.enabled=true}
 * and run as a separate container with profile {@code indexer}.</p>
 */
@Component
@ConditionalOnProperty(name = "phoenixkey.indexer.enabled", havingValue = "true")
@Slf4j
public class BlockfrostPoller {

    @Scheduled(fixedDelayString = "${phoenixkey.indexer.poll-interval-ms:20000}")
    public void poll() {
        log.debug("Indexer poll — stub, no-op until Aiken validator deploys");
    }
}
