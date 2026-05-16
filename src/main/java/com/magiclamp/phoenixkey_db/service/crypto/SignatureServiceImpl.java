package com.magiclamp.phoenixkey_db.service.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;

/**
 * Signature verification — supports BOTH Ed25519 (TAAD_Key) and P-256
 * (HW_Key fallback) qua dispatch theo độ dài pubkey hex.
 *
 * <p>Sau quyết định curve-alignment.md (xem docs/decisions/), TAAD_Key Ed25519
 * là path chính cho mọi mutation. HW_Key chỉ gate biometric local trong
 * Secure Enclave, không bao giờ ký lên server. Code P-256 giữ lại như
 * fallback để hỗ trợ device cũ trong giai đoạn migration.</p>
 *
 * <p>Format signature:
 * <ul>
 *   <li>Ed25519: 64-byte raw (R || S), không có DER wrapper</li>
 *   <li>P-256: DER encoded ECDSA signature</li>
 * </ul>
 * </p>
 *
 * <p>Format pubkey hex:
 * <ul>
 *   <li>Ed25519: 64 hex chars (32 bytes compressed point)</li>
 *   <li>P-256 uncompressed: 130 hex chars (65 bytes, 0x04 prefix)</li>
 *   <li>P-256 compressed: 66 hex chars (33 bytes, 0x02/0x03 prefix)</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
public class SignatureServiceImpl implements SignatureService {

    private static final String GENESIS_PREFIX = "PHOENIXKEY_GENESIS:";

    @PostConstruct
    void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
            log.info("BouncyCastle provider registered (Ed25519 + secp256r1 + secp256k1)");
        }
    }

    @Override
    public boolean verifyEcdsa(String publicKeyHex, byte[] message, byte[] signature) {
        if (publicKeyHex == null || message == null || signature == null) {
            return false;
        }
        try {
            CurveType curve = detectCurve(publicKeyHex);
            return switch (curve) {
                case ED25519 -> verifyEd25519(publicKeyHex, message, signature);
                case P256    -> verifyP256(publicKeyHex, message, signature);
            };
        } catch (Exception e) {
            log.warn("verifyEcdsa failed (pubkeyLen={}, sigLen={}): {}",
                    publicKeyHex.length(), signature.length, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean verifyGenesis(String publicKeyHex, byte[] signature) {
        byte[] message = (GENESIS_PREFIX + publicKeyHex).getBytes(StandardCharsets.UTF_8);
        return verifyEcdsa(publicKeyHex, message, signature);
    }

    // ─── Ed25519 ──────────────────────────────────────────────────

    private boolean verifyEd25519(String publicKeyHex, byte[] message, byte[] signature) {
        if (signature.length != 64) {
            log.warn("Ed25519 signature must be 64 bytes, got {}", signature.length);
            return false;
        }
        byte[] pubKeyBytes = hexToBytes(publicKeyHex);
        if (pubKeyBytes.length != 32) {
            log.warn("Ed25519 pubkey must be 32 bytes, got {}", pubKeyBytes.length);
            return false;
        }
        Ed25519PublicKeyParameters pubKey = new Ed25519PublicKeyParameters(pubKeyBytes, 0);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(false, pubKey);
        signer.update(message, 0, message.length);
        return signer.verifySignature(signature);
    }

    // ─── P-256 (legacy / fallback) ────────────────────────────────

    private boolean verifyP256(String publicKeyHex, byte[] message, byte[] signature) throws Exception {
        PublicKey pubKey = decodeP256PublicKey(publicKeyHex);
        Signature sig = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME);
        sig.initVerify(pubKey);
        sig.update(message);
        return sig.verify(signature);
    }

    private PublicKey decodeP256PublicKey(String publicKeyHex) throws Exception {
        byte[] pubKeyBytes = hexToBytes(publicKeyHex);
        var params = ECNamedCurveTable.getParameterSpec("secp256r1");
        var q = params.getCurve().decodePoint(pubKeyBytes);
        ECPublicKeySpec keySpec = new ECPublicKeySpec(q, params);
        KeyFactory kf = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
        return kf.generatePublic(keySpec);
    }

    // ─── Curve detection ──────────────────────────────────────────

    private enum CurveType { ED25519, P256 }

    private CurveType detectCurve(String publicKeyHex) {
        String hex = publicKeyHex.toLowerCase();
        int len = hex.length();
        if (len == 64) return CurveType.ED25519;
        if (len == 66 && (hex.startsWith("02") || hex.startsWith("03"))) return CurveType.P256;
        if (len == 130 && hex.startsWith("04")) return CurveType.P256;
        throw new IllegalArgumentException(
                "Unrecognized pubkey format. Ed25519=64hex, P-256=66/130hex. Got len=" + len);
    }

    private static byte[] hexToBytes(String hex) {
        String s = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("hex length must be even: " + s.length());
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
