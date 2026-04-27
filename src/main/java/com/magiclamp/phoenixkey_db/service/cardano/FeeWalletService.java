package com.magiclamp.phoenixkey_db.service.cardano;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Network;
import com.magiclamp.phoenixkey_db.service.secret.VaultSecretService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Fee wallet — server-side wallet trả gas Cardano cho mọi DID operation.
 *
 * Mnemonic load từ Vault tại {@code secret/phoenixkey/fee-wallet/mnemonic} ở
 * {@link #init()}. Thread-safe sau init: {@link Account} immutable.
 *
 * <p><b>Threat model:</b> Mnemonic bị lộ = attacker drain ADA của fee wallet.
 * KHÔNG kiểm soát được key user (Hardware Key vẫn ở Secure Enclave mobile).
 * Blast radius giới hạn ở số dư fee wallet (giữ thấp 50–100 ADA, monitor balance).</p>
 */
@Service
@Slf4j
public class FeeWalletService {

    private final VaultSecretService vaultSecretService;
    private final Network network;

    private Account account;

    public FeeWalletService(VaultSecretService vaultSecretService, Network network) {
        this.vaultSecretService = vaultSecretService;
        this.network = network;
    }

    @PostConstruct
    void init() {
        String[] mnemonic = vaultSecretService.getFeeWalletMnemonic();
        if (mnemonic.length != 24 && mnemonic.length != 15 && mnemonic.length != 12) {
            log.warn("Fee wallet mnemonic length={} không phải BIP-39 chuẩn (12/15/24)", mnemonic.length);
        }
        this.account = new Account(network, String.join(" ", mnemonic));
        log.info("FeeWalletService initialized: address={}",
                truncateAddress(account.baseAddress()));
    }

    /** Bech32 address dùng nhận tADA + làm change/registry address. */
    public String address() {
        return account.baseAddress();
    }

    /** {@link Account} object để dùng trong tx builder ({@code .sign(account)}). */
    public Account account() {
        return account;
    }

    private static String truncateAddress(String addr) {
        if (addr == null || addr.length() < 20) {
            return addr;
        }
        return addr.substring(0, 12) + "..." + addr.substring(addr.length() - 8);
    }
}
