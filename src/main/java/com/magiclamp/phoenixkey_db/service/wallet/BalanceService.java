package com.magiclamp.phoenixkey_db.service.wallet;

import com.magiclamp.phoenixkey_db.dto.wallet.WalletDtos.BalanceResponse;
import com.magiclamp.phoenixkey_db.dto.wallet.WalletDtos.MagicClaimResponse;

public interface BalanceService {

    /**
     * Compute and return current balances + accrued MAGIC.
     * Caches address lookup 15s to avoid hammering Blockfrost.
     */
    BalanceResponse getBalance(String userDid);

    /**
     * Register or update user's wallet address (idempotent).
     */
    void registerWalletAddress(String userDid, String walletAddress);

    /**
     * Claim accrued MAGIC — server signs Cardano mint tx with emission authority key,
     * sends MAGIC to user's wallet address. Idempotent within 60s window.
     */
    MagicClaimResponse claimMagic(String userDid);
}
