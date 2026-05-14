package com.magiclamp.phoenixkey_db.service.recovery;

import com.magiclamp.phoenixkey_db.dto.recovery.RecoveryDtos.*;

import java.util.UUID;

public interface RecoveryService {

    /**
     * Guardian-initiated recovery. Requires ≥ min_guardian_sigs (default 2)
     * guardian signatures and a collateral output (50 ADA preprod).
     * Builds Cardano tx with redeemer InitRecovery, submits.
     * Returns recovery_id for tracking.
     */
    InitRecoveryResponse init(InitRecoveryRequest request);

    /**
     * Owner cancels a recovery in progress. Requires current controller signature.
     */
    void cancel(UUID recoveryId, String userDid, CancelRecoveryRequest request);

    /**
     * Anyone can finalize after deadline_slot elapsed.
     */
    FinalizeRecoveryResponse finalize(UUID recoveryId);

    /**
     * Status lookup.
     */
    RecoveryStatusResponse getStatus(String userDid);
}
