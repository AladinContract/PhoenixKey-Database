package com.magiclamp.phoenixkey_db.service.cardano;

import com.magiclamp.phoenixkey_db.service.cardano.dto.TxResult;
import com.magiclamp.phoenixkey_db.service.cardano.dto.W3CDIDDocument;

import java.util.Optional;

/**
 * Service quản lý W3C DID Document trên Cardano.
 *
 * <p>Mỗi DID Document = inline datum trong UTxO tại fee-wallet address (PoC).
 * Production sẽ chuyển sang Plutus validator script address.</p>
 *
 * <p>DID format: {@code did:cardano:<network>:<genesis_tx_hash>}.</p>
 *
 * <p>Reference: {@code PhoenixKey-Backend/src/did-registry-onchain.ts} (TypeScript +
 * MeshSDK). Java port qua BloxBean cardano-client-lib 0.6.4.</p>
 */
public interface CardanoService {

    /**
     * Genesis: publish DID Document mới lên Cardano.
     *
     * <p>Flow:
     * <ol>
     *   <li>Build W3C DID Document với placeholder DID (tx hash chưa biết)</li>
     *   <li>Build tx: gửi 10 ADA tới fee-wallet address kèm inline datum (JSON)</li>
     *   <li>Sign bằng fee wallet → submit qua Blockfrost</li>
     *   <li>Chờ tx confirm</li>
     *   <li>Cập nhật DID Document với DID thật {@code did:cardano:<network>:<txHash>}</li>
     * </ol>
     *
     * @param publicKeyHex Hardware Key hex từ Secure Enclave mobile
     * @return TxResult kèm tx hash + DID string
     */
    TxResult createDID(String publicKeyHex);

    /**
     * Update: rotate key (consume UTxO cũ + tạo UTxO mới với datum mới).
     *
     * <p>MVP: fee wallet ký full tx (KHÔNG required signer của old key) — vi phạm
     * Zero-Trust spec, chấp nhận tạm thời. Phase H sẽ yêu cầu mobile cung cấp
     * partial-signed CBOR từ old controller key.</p>
     *
     * @param newPublicKeyHex          public key mới (thay key cũ)
     * @param previousTxHash           tx hash chứa DID Document hiện tại
     * @param oldControllerSignedTx    CBOR partial-signed bởi mobile (null cho MVP)
     */
    TxResult updateDID(String newPublicKeyHex, String previousTxHash, String oldControllerSignedTx);

    /**
     * Resolve DID Document từ tx hash. Fetch UTxO outputs của tx, tìm output có
     * inline datum chứa JSON khớp shape {@link W3CDIDDocument}.
     */
    W3CDIDDocument resolve(String txHash);

    /**
     * Tìm UTxO chưa chi tiêu chứa DID Document hiện tại của một DID. Dùng cho
     * UPDATE flow (cần consume UTxO cũ).
     */
    Optional<UtxoWithDoc> findDIDUtxo(String did);

    /** UTxO + DID Document đã decode — kết quả của {@link #findDIDUtxo}. */
    record UtxoWithDoc(String txHash, int outputIndex, W3CDIDDocument doc) {
    }
}
