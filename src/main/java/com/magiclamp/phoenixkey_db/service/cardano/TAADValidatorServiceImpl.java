package com.magiclamp.phoenixkey_db.service.cardano;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Build + submit Cardano tx that interact with the TAAD Plutus validator.
 *
 * <p><b>TODO (Lợi + Tuân):</b> implement once {@code phoenixkey-validator}
 * Aiken contracts are deployed to preprod and {@code taad-script-cbor-hex} +
 * {@code taad-script-address} are populated.</p>
 *
 * <p>The Aiken datum + redeemer schemas live in
 * {@code phoenixkey-validator/lib/phoenixkey/types.ak} and must be matched
 * exactly by the BloxBean PlutusData builders here.</p>
 */
@Service
@Slf4j
public class TAADValidatorServiceImpl implements TAADValidatorService {

    @Override
    public String submitInitRecovery(String userDid, String newPubkeyHex,
                                      int guardianSigCount, String collateralAddress,
                                      long collateralLovelace) {
        throw new UnsupportedOperationException(
                "TAADValidator submitInitRecovery — wire BloxBean + Aiken script CBOR after deploy");
    }

    @Override
    public String submitCancelRecovery(String userDid) {
        throw new UnsupportedOperationException(
                "TAADValidator submitCancelRecovery — pending validator deploy");
    }

    @Override
    public String submitFinalizeRecovery(String userDid, String newPubkeyHex) {
        throw new UnsupportedOperationException(
                "TAADValidator submitFinalizeRecovery — pending validator deploy");
    }
}
