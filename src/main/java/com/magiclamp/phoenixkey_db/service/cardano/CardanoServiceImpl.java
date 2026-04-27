package com.magiclamp.phoenixkey_db.service.cardano;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.service.cardano.dto.TxResult;
import com.magiclamp.phoenixkey_db.service.cardano.dto.W3CDIDDocument;
import com.magiclamp.phoenixkey_db.service.cardano.dto.W3CVerificationMethod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation của {@link CardanoService} qua BloxBean cardano-client-lib + Blockfrost.
 *
 * <p>Mỗi DID Document = inline datum (Babbage era+) trong UTxO tại fee-wallet
 * address. Genesis tx hash → DID string {@code did:cardano:<network>:<txHash>}.</p>
 *
 * <p>Reference: {@code PhoenixKey-Backend/src/did-registry-onchain.ts} (TS + Mesh).
 * Java port qua BloxBean QuickTxBuilder.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardanoServiceImpl implements CardanoService {

    /** ADA chuyển vào UTxO genesis. Tối thiểu để hold inline datum + min UTxO. */
    private static final double GENESIS_ADA = 5.0;

    private static final String DID_PREFIX = "did:cardano:";

    private final BackendService backendService;
    private final FeeWalletService feeWalletService;
    private final CardanoConfig config;
    private final ObjectMapper objectMapper;

    @Override
    public TxResult createDID(String publicKeyHex) {
        // 1. Build W3C DID Document. DID id = placeholder vì tx hash chưa biết —
        //    resolve() sẽ override id từ tx hash khi đọc lại.
        String placeholderDid = DID_PREFIX + config.network() + ":pending";
        W3CDIDDocument doc = buildInitialDocument(placeholderDid, publicKeyHex);

        // 2. Serialize JSON → bytes → Plutus inline datum.
        byte[] datumBytes;
        try {
            datumBytes = objectMapper.writeValueAsBytes(doc);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.CARDANO_TX_FAILED,
                    "Failed to serialize DID Document: " + e.getMessage());
        }
        PlutusData inlineDatum = BytesPlutusData.of(datumBytes);

        // 3. Build tx: pay 5 ADA tới fee-wallet address (self) kèm inline datum.
        //    Dùng payToContract (không yêu cầu address phải là script) để attach
        //    inline datum cho output. payToAddress 0.6.4 chỉ cho datum hash hoặc
        //    Script, không cho PlutusData.
        String address = feeWalletService.address();
        Tx tx = new Tx()
                .payToContract(address, Amount.ada(GENESIS_ADA), inlineDatum)
                .from(address);

        // 4. Sign + submit + wait confirmation.
        log.info("createDID: submitting tx for publicKeyHex={}, datumSize={}B",
                truncate(publicKeyHex), datumBytes.length);
        Result<String> result;
        try {
            result = new QuickTxBuilder(backendService)
                    .compose(tx)
                    .withSigner(SignerProviders.signerFrom(feeWalletService.account()))
                    .completeAndWait(log::info);
        } catch (Exception e) {
            throw new AppException(ErrorCode.CARDANO_TX_FAILED,
                    "createDID submit failed: " + e.getMessage());
        }

        if (result == null || !result.isSuccessful()) {
            throw new AppException(ErrorCode.CARDANO_TX_FAILED,
                    "createDID failed: " + (result == null ? "null result" : result.getResponse()));
        }

        String txHash = result.getValue();
        String did = DID_PREFIX + config.network() + ":" + txHash;
        log.info("createDID: success — did={}, txHash={}", did, txHash);

        return new TxResult(txHash, did, "CREATE");
    }

    @Override
    public TxResult updateDID(String newPublicKeyHex, String previousTxHash, String oldControllerSignedTx) {
        // TODO[Phase B.3c]: consume UTxO cũ + tạo UTxO mới với datum mới.
        throw new UnsupportedOperationException("updateDID not implemented — see Phase B.3c");
    }

    @Override
    public W3CDIDDocument resolve(String txHash) {
        TxContentUtxoOutputs out = findInlineDatumOutput(txHash)
                .orElseThrow(() -> new AppException(ErrorCode.CARDANO_RESOLVE_FAILED,
                        "no inline datum in tx: " + txHash));
        return decodeDocument(out.getInlineDatum(), txHash);
    }

    @Override
    public Optional<UtxoWithDoc> findDIDUtxo(String did) {
        String txHash = extractTxHash(did);
        if (txHash == null) {
            throw new AppException(ErrorCode.CARDANO_RESOLVE_FAILED,
                    "Invalid DID format: " + did);
        }

        TxContentUtxo content = fetchTxContent(txHash).orElse(null);
        if (content == null || content.getOutputs() == null) {
            return Optional.empty();
        }

        List<TxContentUtxoOutputs> outputs = content.getOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            TxContentUtxoOutputs out = outputs.get(i);
            if (hasInlineDatum(out)) {
                W3CDIDDocument doc = decodeDocument(out.getInlineDatum(), txHash);
                return Optional.of(new UtxoWithDoc(txHash, i, doc));
            }
        }
        return Optional.empty();
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    /**
     * Build initial DID Document với 1 verification method cho Hardware Key.
     * Cả {@code authentication}, {@code assertionMethod}, {@code capabilityInvocation}
     * đều tham chiếu cùng key — owner có toàn quyền lúc Genesis. Spec §11 sẽ
     * scope-down qua Key Rotation (tách quyền giữa các key).
     */
    private W3CDIDDocument buildInitialDocument(String did, String publicKeyHex) {
        String keyId = did + "#key-1";
        W3CVerificationMethod vm = new W3CVerificationMethod(
                keyId,
                W3CDIDDocument.KEY_TYPE_SECP256K1,
                did,
                publicKeyHex);
        return new W3CDIDDocument(
                List.of(W3CDIDDocument.CONTEXT_V1),
                did,
                did,
                List.of(vm),
                List.of(keyId),
                List.of(keyId),
                List.of(keyId),
                Instant.now().toString(),
                null);
    }

    private static String truncate(String s) {
        if (s == null || s.length() <= 12) {
            return s;
        }
        return s.substring(0, 12) + "...";
    }

    // ──────────────────────────────────────────────────────────────
    // Resolve helpers
    // ──────────────────────────────────────────────────────────────

    private static final Pattern DID_PATTERN =
            Pattern.compile("^did:cardano:[^:]+:([a-f0-9]{64})$");

    /** Extract tx hash từ DID string {@code did:cardano:<network>:<txHash>}. */
    private static String extractTxHash(String did) {
        if (did == null) {
            return null;
        }
        Matcher m = DID_PATTERN.matcher(did);
        return m.matches() ? m.group(1) : null;
    }

    private Optional<TxContentUtxo> fetchTxContent(String txHash) {
        Result<TxContentUtxo> result;
        try {
            result = backendService.getTransactionService().getTransactionUtxos(txHash);
        } catch (Exception e) {
            throw new AppException(ErrorCode.CARDANO_RESOLVE_FAILED,
                    "fetch tx outputs failed: " + e.getMessage());
        }
        if (result == null || !result.isSuccessful() || result.getValue() == null) {
            return Optional.empty();
        }
        return Optional.of(result.getValue());
    }

    private Optional<TxContentUtxoOutputs> findInlineDatumOutput(String txHash) {
        TxContentUtxo content = fetchTxContent(txHash)
                .orElseThrow(() -> new AppException(ErrorCode.CARDANO_RESOLVE_FAILED,
                        "tx not found: " + txHash));
        if (content.getOutputs() == null) {
            return Optional.empty();
        }
        return content.getOutputs().stream()
                .filter(CardanoServiceImpl::hasInlineDatum)
                .findFirst();
    }

    private static boolean hasInlineDatum(TxContentUtxoOutputs out) {
        return out.getInlineDatum() != null && !out.getInlineDatum().isBlank();
    }

    /**
     * Decode CBOR-encoded inline datum → BytesPlutusData → JSON → W3CDIDDocument.
     * Cũng override placeholder DID ({@code did:cardano:<network>:pending}) bằng
     * tx hash thực tế.
     */
    private W3CDIDDocument decodeDocument(String inlineDatumHex, String txHash) {
        try {
            byte[] cbor = HexUtil.decodeHexString(inlineDatumHex);
            PlutusData datum = PlutusData.deserialize(cbor);
            if (!(datum instanceof BytesPlutusData bytes)) {
                throw new AppException(ErrorCode.CARDANO_RESOLVE_FAILED,
                        "datum không phải BytesPlutusData (got "
                                + datum.getClass().getSimpleName() + ")");
            }
            byte[] jsonBytes = bytes.getValue();
            W3CDIDDocument doc = objectMapper.readValue(jsonBytes, W3CDIDDocument.class);
            return overrideDid(doc, txHash);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.CARDANO_RESOLVE_FAILED,
                    "decode datum failed: " + e.getMessage());
        }
    }

    /**
     * Replace placeholder {@code did:cardano:<network>:pending} với DID thực dựa
     * trên tx hash. Update cả {@code id}, {@code controller}, và VM ids.
     */
    private W3CDIDDocument overrideDid(W3CDIDDocument original, String txHash) {
        String realDid = DID_PREFIX + config.network() + ":" + txHash;
        List<W3CVerificationMethod> vms = original.verificationMethod() == null
                ? List.of()
                : original.verificationMethod().stream()
                        .map(vm -> new W3CVerificationMethod(
                                replacePending(vm.id(), txHash),
                                vm.type(),
                                realDid,
                                vm.publicKeyHex()))
                        .toList();
        return new W3CDIDDocument(
                original.context(),
                realDid,
                realDid,
                vms,
                replaceAllIds(original.authentication(), txHash),
                replaceAllIds(original.assertionMethod(), txHash),
                replaceAllIds(original.capabilityInvocation(), txHash),
                original.created(),
                original.updated());
    }

    private static List<String> replaceAllIds(List<String> ids, String txHash) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().map(s -> replacePending(s, txHash)).toList();
    }

    private static String replacePending(String s, String txHash) {
        return s == null ? null : s.replace(":pending", ":" + txHash);
    }
}
