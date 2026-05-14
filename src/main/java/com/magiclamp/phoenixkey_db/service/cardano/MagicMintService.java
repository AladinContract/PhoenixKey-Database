package com.magiclamp.phoenixkey_db.service.cardano;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Mint MAGIC tokens via Plutus V3 emission policy + send to recipient.
 *
 * <p><b>TODO (Lợi):</b> wire BloxBean QuickTxBuilder once
 * {@code phoenixkey.cardano.magic-policy-id} +
 * {@code phoenixkey.cardano.magic-script-cbor-hex} are populated from the
 * {@code phoenixkey-validator} Aiken deploy. Authority signing key loaded
 * from Vault path {@code secret/phoenixkey/emission/mnemonic}.</p>
 *
 * <p>Current stub: throws {@link UnsupportedOperationException} unless
 * {@code phoenixkey.cardano.magic-policy-id} is set, in which case it still
 * throws but with a clearer "not yet implemented" message. Bot endpoint
 * /wallet/magic/claim will surface this as ErrorCode.CARDANO_TX_FAILED.</p>
 */
@Service
@Slf4j
public class MagicMintService {

    @Value("${phoenixkey.cardano.magic-policy-id:}")
    private String magicPolicyId;

    public String mintAndSend(String recipientAddress, long amount) {
        if (magicPolicyId == null || magicPolicyId.isEmpty()) {
            throw new UnsupportedOperationException(
                    "MAGIC policy not deployed yet — set phoenixkey.cardano.magic-policy-id "
                            + "after Aiken validator deploy");
        }
        // TODO: build mint tx via QuickTxBuilder, sign with emission authority,
        //  submit. See phoenixkey-validator/validators/magic_policy.ak for the
        //  parameter that must be baked in (emission_authority_pkh).
        throw new UnsupportedOperationException(
                "MagicMintService.mintAndSend not yet wired — pending Aiken validator deploy");
    }
}
