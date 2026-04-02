package com.magiclamp.phoenixkey_db.crypto;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.magiclamp.phoenixkey_db.service.PepperVaultService;

/**
 * Blind Index Service — triển khai HMAC-SHA256 + Pepper để hash credentials.
 *
 * Zero-PII: Thay vì lưu email/SĐT plaintext, hệ thống lưu
 * {@code HMAC-SHA256(input, SERVER_PEPPER)}.
 * Nếu DB bị hack, hacker chỉ nhận được hash vô nghĩa.
 *
 * Pepper được đọc từ HashiCorp Vault (PepperVaultService) tại khởi động.
 * Vault chứa TẤT CẢ pepper (hiện tại + lịch sử) để hỗ trợ multi-version
 * lookup khi pepper được rotate.
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

    private final PepperVaultService pepperVaultService;

    public BlindIndexService(PepperVaultService pepperVaultService) {
        this.pepperVaultService = pepperVaultService;
    }

    /**
     * Hash credential bằng HMAC-SHA256 + Pepper hiện tại.
     *
     * @param credential email hoặc số điện thoại thuần
     * @return hex string 64 ký tự
     */
    public String hash(String credential) {
        return computeHmac(credential, pepperVaultService.getCurrentPepper());
    }

    /**
     * Hash credential với phiên bản Pepper cụ thể.
     * Dùng cho multi-version lookup khi credential được re-hash
     * sau khi pepper được rotate.
     *
     * @param credential    email/SĐT thuần
     * @param pepperVersion pepper version trong DB
     * @return hex string 64 ký tự
     */
    public String hash(String credential, int pepperVersion) {
        return computeHmac(credential, pepperVaultService.getPepper(pepperVersion));
    }

    /**
     * Verify credential khớp với hash đã lưu.
     * Dùng pepper HIỆN TẠI.
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
     * Dùng khi credential chưa được re-hash (pepper_version trong DB
     * nhỏ hơn current pepper_version trên Vault).
     *
     * @param credential    email/SĐT thuần
     * @param storedHash    hash đã lưu trong DB
     * @param pepperVersion pepper version trong DB
     * @return true nếu khớp
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

    /** Phiên bản pepper hiện tại (từ Vault). */
    public int getCurrentPepperVersion() {
        return pepperVaultService.getCurrentVersion();
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