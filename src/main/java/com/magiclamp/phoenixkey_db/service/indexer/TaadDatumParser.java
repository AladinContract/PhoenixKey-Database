package com.magiclamp.phoenixkey_db.service.indexer;

import com.magiclamp.phoenixkey_db.dto.indexer.SyncTaadRequest;
import org.springframework.stereotype.Component;

/**
 * Parses TAAD UTxO inline datum CBOR into a SyncTaadRequest.
 *
 * <p><b>TODO (Lợi):</b> implement once validator datum schema is finalized.
 * Stub returns null — callers must null-check.</p>
 */
@Component
public class TaadDatumParser {
    public SyncTaadRequest parse(String datumCborHex, long blockSlot, String blockHash) {
        return null;
    }
}
