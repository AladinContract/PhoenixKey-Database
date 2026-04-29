package com.magiclamp.phoenixkey_db.service.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;

/**
 * Verify ECDSA secp256k1 qua BouncyCastle. JCE provider mặc định không support
 * secp256k1 — phải register BC provider.
 *
 * <p>Signature format: DER (chuẩn từ Apple Secure Enclave + Android Keystore khi
 * sign với ECDSA). Format chuẩn hoá đã chốt PLAN-Server.md decision #2.</p>
 */
@Service
@Slf4j
public class SignatureServiceImpl implements SignatureService {

    private static final String CURVE = "secp256k1";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String GENESIS_PREFIX = "PHOENIXKEY_GENESIS:";

    @PostConstruct
    void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
            log.info("BouncyCastle provider registered for secp256k1 ECDSA verify");
        }
    }

    @Override
    public boolean verifyEcdsa(String publicKeyHex, byte[] message, byte[] signature) {
        try {
            PublicKey pubKey = decodePublicKey(publicKeyHex);
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            sig.initVerify(pubKey);
            sig.update(message);
            return sig.verify(signature);
        } catch (Exception e) {
            log.warn("verifyEcdsa failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean verifyGenesis(String publicKeyHex, byte[] derSignature) {
        byte[] message = (GENESIS_PREFIX + publicKeyHex).getBytes(StandardCharsets.UTF_8);
        return verifyEcdsa(publicKeyHex, message, derSignature);
    }

    /**
     * Decode public key từ hex string (compressed 33B hoặc uncompressed 65B) sang
     * {@link PublicKey} gắn với curve secp256k1.
     */
    private PublicKey decodePublicKey(String publicKeyHex) throws Exception {
        byte[] pubKeyBytes = hexToBytes(publicKeyHex);
        ECNamedCurveParameterSpec params = ECNamedCurveTable.getParameterSpec(CURVE);
        org.bouncycastle.math.ec.ECPoint q = params.getCurve().decodePoint(pubKeyBytes);
        ECPublicKeySpec keySpec = new ECPublicKeySpec(q, params);
        KeyFactory kf = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
        return kf.generatePublic(keySpec);
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("hex string is null");
        }
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
