package com.magiclamp.phoenixkey_db.service.cardano;

/**
 * Build + submit transactions that interact with the TAAD Plutus validator.
 * Used by RecoveryService.
 *
 * <p>Implementation uses BloxBean QuickTxBuilder. Reference script for TAAD
 * is deployed once at boot; subsequent spends reference it to save fees.</p>
 */
public interface TAADValidatorService {

    /**
     * Submit a tx with redeemer InitRecovery — locks collateral and shifts
     * TAAD UTxO status to Recovering.
     */
    String submitInitRecovery(
            String userDid,
            String newPubkeyHex,
            int guardianSigCount,
            String collateralAddress,
            long collateralLovelace
    );

    /**
     * Submit a tx with redeemer CancelRecovery — owner aborts recovery,
     * returns collateral to the original guardian.
     */
    String submitCancelRecovery(String userDid);

    /**
     * Submit a tx with redeemer FinalizeRecovery — anyone, after deadline.
     */
    String submitFinalizeRecovery(String userDid, String newPubkeyHex);
}
