package com.magiclamp.phoenixkey_db.service.cardano;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magiclamp.phoenixkey_db.service.cardano.dto.TxResult;
import com.magiclamp.phoenixkey_db.service.cardano.dto.W3CDIDDocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation của {@link CardanoService}. Methods chưa implement — sẽ port
 * trong Phase B.3a/3b/3c (xem PLAN-Server.md).
 *
 * Wired deps:
 * <ul>
 *   <li>{@link BackendService} — Blockfrost adapter cho fetch UTxO + submit tx</li>
 *   <li>{@link FeeWalletService} — fee wallet để ký + chi gas</li>
 *   <li>{@link CardanoConfig} — network + confirmTimeoutMs</li>
 *   <li>{@link ObjectMapper} — encode/decode DID Document JSON datum</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardanoServiceImpl implements CardanoService {

    @SuppressWarnings("unused") // wired in B.3
    private final BackendService backendService;
    @SuppressWarnings("unused")
    private final FeeWalletService feeWalletService;
    @SuppressWarnings("unused")
    private final CardanoConfig config;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;

    @Override
    public TxResult createDID(String publicKeyHex) {
        // TODO[Phase B.3a]: build tx với inline datum, sign bằng fee wallet, submit.
        throw new UnsupportedOperationException("createDID not implemented — see Phase B.3a");
    }

    @Override
    public TxResult updateDID(String newPublicKeyHex, String previousTxHash, String oldControllerSignedTx) {
        // TODO[Phase B.3c]: consume UTxO cũ + tạo UTxO mới với datum mới.
        throw new UnsupportedOperationException("updateDID not implemented — see Phase B.3c");
    }

    @Override
    public W3CDIDDocument resolve(String txHash) {
        // TODO[Phase B.3b]: fetch tx outputs, tìm output có inline datum, decode JSON.
        throw new UnsupportedOperationException("resolve not implemented — see Phase B.3b");
    }

    @Override
    public Optional<UtxoWithDoc> findDIDUtxo(String did) {
        // TODO[Phase B.3b]: scan unspent outputs tại fee-wallet address.
        throw new UnsupportedOperationException("findDIDUtxo not implemented — see Phase B.3b");
    }
}
