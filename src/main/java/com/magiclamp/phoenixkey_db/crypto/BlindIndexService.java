package com.magiclamp.phoenixkey_db.crypto;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Blind Index Service — triển khai HMAC-SHA256 + Pepper để hash credentials.
 *
 * Zero-PII: Thay vì lưu email/SĐT plaintext, hệ thống lưu
 * {@code HMAC-SHA256(input, SERVER_PEPPER)}.
 * Nếu DB bị hack, hacker chỉ nhận được hash vô nghĩa.
 *
 * Pepper Rotation (6 tháng/lần):
 * Khi Pepper xoay vòng trên Vault, {@code pepperVersion} tăng lên.
 * Hệ thống phải re-hash credentials cũ ở lần đăng nhập tiếp theo của user.
 *
 * Tại sao HMAC-SHA256 thay vì SHA-256 thường?
 * SHA-256 đơn giản dễ bị tấn công Rainbow Table.
 * HMAC thêm key bí mật (Pepper) → không thể compute hash mà không có Pepper.
 *
 */
@Service
public class BlindIndexService {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String HASH_ALGO = "SHA-256";

    /** Pepper hiện tại — đọc từ HashiCorp Vault khi khởi động. */
    private final String currentPepper;

    /** Phiên bản Pepper hiện tại. */
    private final int currentPepperVersion;

    public BlindIndexService(
            @Value("${phoenixkey.pepper}") String pepper,
            @Value("${phoenixkey.pepper-version:1}") int pepperVersion) {
        this.currentPepper = pepper;
        this.currentPepperVersion = pepperVersion;
    }

    /** Lấy phiên bản pepper hiện tại. */
    public int getCurrentPepperVersion() {
        return currentPepperVersion;
    }

    /**
     * Hash credential bằng HMAC-SHA256 + Pepper hiện tại.
     *
     * @param credential email hoặc số điện thoại thuần
     * @return hex string 64 ký tự
     */
    public String hash(String credential) {
        return computeHmac(credential, currentPepper);
    }

    /**
     * Hash credential với phiên bản Pepper cụ thể.
     * Chỉ hỗ trợ current version. Production cần multi-version lookup.
     */
    public String hash(String credential, int pepperVersion) {
        if (pepperVersion == currentPepperVersion) {
            return hash(credential);
        }
        throw new IllegalStateException(
                "Pepper version " + pepperVersion + " not found. "
                        + "Multi-version pepper support chưa được triển khai.");
    }

    /**
     * Verify credential khớp với hash đã lưu.
     *
     * @param credential email/SĐT thuần
     * @param storedHash hash đã lưu trong DB
     * @return true nếu khớp
     */
    public boolean verify(String credential, String storedHash) {
        return MessageDigest.isEqual(
                hash(credential).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Verify credential với hash và pepper version cụ thể.
     */
    public boolean verify(String credential, String storedHash, int pepperVersion) {
        return MessageDigest.isEqual(
                hash(credential, pepperVersion).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA-256 hash cho IP/fingerprint (không HMAC).
     * Dùng trong ActivityLog metadata.
     */
    public String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGO);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String computeHmac(String data, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(keySpec);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }
}
