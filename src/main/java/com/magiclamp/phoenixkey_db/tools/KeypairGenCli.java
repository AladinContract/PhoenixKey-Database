package com.magiclamp.phoenixkey_db.tools;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.UUID;

/**
 * Dev CLI: sinh secp256k1 keypair + ký test message cho register / rotate flow.
 *
 * <p>Cách dùng (qua wrapper script {@code tools/keygen}):
 * <pre>
 *   ./tools/keygen genesis
 *       Sinh keypair mới + sign GENESIS message + in curl command để register.
 *
 *   ./tools/keygen rotate &lt;privKeyHex&gt; &lt;newPubKeyHex&gt; [nonce]
 *       Ký ROTATE message với key cũ. Nếu không truyền nonce, tự generate UUIDv4.
 *
 *   ./tools/keygen sign &lt;privKeyHex&gt; &lt;message&gt;
 *       Ký arbitrary message.
 * </pre>
 *
 * <p>Hoặc gọi trực tiếp:
 * <pre>
 *   ./mvnw -q exec:java -Dexec.mainClass="com.magiclamp.phoenixkey_db.tools.KeypairGenCli" \
 *     -Dexec.args="genesis"
 * </pre>
 *
 * <p><b>Dev only — KHÔNG dùng cho prod.</b> Production keypair sinh trong Secure
 * Enclave/TEE của mobile, không bao giờ tồn tại trên server.
 */
@SuppressWarnings({ "java:S106", "java:S112" }) // CLI: System.out/err + generic Exception là intentional
public final class KeypairGenCli {

    private static final String CURVE = "secp256k1";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String GENESIS_PREFIX = "PHOENIXKEY_GENESIS:";
    private static final String ROTATE_PREFIX = "PHOENIXKEY_ROTATE:";

    private KeypairGenCli() {
    }

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "wallet":
                cmdWallet();
                break;
            case "genesis":
                cmdGenesis();
                break;
            case "rotate":
                if (args.length < 3) {
                    System.err.println("rotate cần: <privKeyHex> <newPubKeyHex> [nonce]");
                    System.exit(2);
                }
                cmdRotate(args[1], args[2], args.length >= 4 ? args[3] : UUID.randomUUID().toString());
                break;
            case "sign":
                if (args.length < 3) {
                    System.err.println("sign cần: <privKeyHex> <message>");
                    System.exit(2);
                }
                cmdSign(args[1], args[2]);
                break;
            default:
                usage();
                System.exit(2);
        }
    }

    /**
     * Sinh BIP-39 24-word mnemonic ngẫu nhiên + Cardano Preprod address. In ra
     * hướng dẫn paste vào seed-dev.sh + faucet để fund.
     *
     * Khác với GENESIS keypair (Hardware Key per-user), wallet này dùng cho
     * fee wallet — server-side, trả gas Cardano cho mọi DID operation.
     */
    static void cmdWallet() {
        Account account = new Account(Networks.preprod());
        String mnemonic = account.mnemonic();
        String address = account.baseAddress();

        out("==> New BIP-39 24-word mnemonic + Cardano Preprod address");
        out("    mnemonic:  " + mnemonic);
        out("    address:   " + address);
        out("");
        out("==> Steps:");
        out("  1. Thêm vào .env (đã gitignored, KHÔNG commit):");
        out("     FEE_WALLET_MNEMONIC=\"" + mnemonic + "\"");
        out("");
        out("  2. Restart vault để wipe inmem + watchdog re-seed (đọc .env):");
        out("     docker compose down vault vault-seed && docker compose up -d vault vault-seed");
        out("");
        out("  3. Fund address từ Preprod faucet:");
        out("     " + address);
        out("     https://docs.cardano.org/cardano-testnet/tools/faucet");
        out("");
        out("  4. Restart app để load mnemonic mới:");
        out("     ./mvnw spring-boot:run");
        out("");
        out("  Production: thay bằng mnemonic riêng, lưu HCP Vault qua `vault kv put`.");
    }

    static void cmdGenesis() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(new ECGenParameterSpec(CURVE), new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        BCECPrivateKey privKey = (BCECPrivateKey) kp.getPrivate();
        BCECPublicKey pubKey = (BCECPublicKey) kp.getPublic();

        // Compressed pubkey (33 bytes: 0x02|0x03 prefix + X coord)
        String pubKeyHex = bytesToHex(pubKey.getQ().getEncoded(true));
        // Pad private key D to 32 bytes (some D values < 2^248 → < 64 hex chars)
        String privKeyHex = padLeft(privKey.getD().toString(16), 64);

        String message = GENESIS_PREFIX + pubKeyHex;
        byte[] sig = signEcdsa(privKey, message.getBytes(StandardCharsets.UTF_8));
        String sigHex = bytesToHex(sig);

        out("==> Generated keypair (KEEP privateKey FOR ROTATE LATER)");
        out("    privateKeyHex:    " + privKeyHex);
        out("    publicKeyHex:     " + pubKeyHex);
        out("");
        out("==> Genesis signature");
        out("    message:          " + message);
        out("    signatureDerHex:  " + sigHex + "  (length=" + sig.length + " bytes)");
        out("");
        out("==> curl POST /identity/register :");
        out("    curl -s -X POST http://localhost:8080/api/v1/identity/register \\");
        out("      -H 'Content-Type: application/json' \\");
        out("      -d '{");
        out("        \"publicKeyHex\":      \"" + pubKeyHex + "\",");
        out("        \"keyOrigin\":         \"SECURE_ENCLAVE\",");
        out("        \"keyRole\":           \"owner\",");
        out("        \"addedBySignature\":  \"" + sigHex + "\"");
        out("      }' | jq");
    }

    static void cmdRotate(String privKeyHex, String newPubKeyHex, String nonce) throws Exception {
        PrivateKey privKey = decodePrivateKey(privKeyHex);
        String message = ROTATE_PREFIX + newPubKeyHex + ":" + nonce;
        byte[] sig = signEcdsa(privKey, message.getBytes(StandardCharsets.UTF_8));
        String sigHex = bytesToHex(sig);

        out("==> Rotate signature");
        out("    nonce:            " + nonce);
        out("    message:          " + message);
        out("    signatureDerHex:  " + sigHex + "  (length=" + sig.length + " bytes)");
        out("");
        out("==> curl POST /keys/rotate :");
        out("    Replace <USER_DID> với DID của user (lấy từ response /identity/register).");
        out("    curl -s -X POST http://localhost:8080/api/v1/keys/rotate \\");
        out("      -H 'Content-Type: application/json' \\");
        out("      -d '{");
        out("        \"userDid\":           \"<USER_DID>\",");
        out("        \"newPublicKeyHex\":   \"" + newPubKeyHex + "\",");
        out("        \"keyOrigin\":         \"SECURE_ENCLAVE\",");
        out("        \"nonce\":             \"" + nonce + "\",");
        out("        \"oldKeySignature\":   \"" + sigHex + "\"");
        out("      }' | jq");
    }

    static void cmdSign(String privKeyHex, String message) throws Exception {
        PrivateKey privKey = decodePrivateKey(privKeyHex);
        byte[] sig = signEcdsa(privKey, message.getBytes(StandardCharsets.UTF_8));
        out(bytesToHex(sig));
    }

    private static byte[] signEcdsa(PrivateKey privKey, byte[] message) throws Exception {
        Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
        sig.initSign(privKey);
        sig.update(message);
        return sig.sign();
    }

    private static PrivateKey decodePrivateKey(String hex) throws Exception {
        BigInteger d = new BigInteger(stripHexPrefix(hex), 16);
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(CURVE);
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(d, spec);
        KeyFactory kf = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
        return kf.generatePrivate(keySpec);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String padLeft(String s, int len) {
        if (s.length() >= len) {
            return s;
        }
        return "0".repeat(len - s.length()) + s;
    }

    private static String stripHexPrefix(String hex) {
        return hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
    }

    private static void out(String s) {
        System.out.println(s);
    }

    private static void usage() {
        out("PhoenixKey dev CLI — sinh keypair + ký message");
        out("");
        out("Usage:");
        out("  ./tools/keygen wallet");
        out("       Sinh BIP-39 24-word mnemonic + Cardano address cho fee wallet");
        out("       (one-off — paste vào seed-dev.sh, fund từ faucet)");
        out("");
        out("  ./tools/keygen genesis");
        out("       Sinh secp256k1 keypair (per-user) + ký GENESIS + in curl /identity/register");
        out("");
        out("  ./tools/keygen rotate <privKeyHex> <newPubKeyHex> [nonce]");
        out("       Ký ROTATE bằng key cũ + in curl /keys/rotate");
        out("       (nonce auto UUIDv4 nếu không truyền)");
        out("");
        out("  ./tools/keygen sign <privKeyHex> <message>");
        out("       Ký arbitrary message, output DER hex");
    }
}
